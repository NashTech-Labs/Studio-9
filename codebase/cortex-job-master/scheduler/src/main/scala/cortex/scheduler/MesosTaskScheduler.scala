package cortex.scheduler

import java.util
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.{ ConcurrentHashMap, CountDownLatch }
import java.util.{ Collections, Date }

import cortex.TaskState.Killed
import cortex.common.Logging
import cortex.common.logging.JMLoggerFactory
import cortex.rpc.TaskRPC
import cortex.task.failed.{ ErrorType, FailedTask }
import cortex.{ CortexException, Task, TaskParams, TaskResult, TaskTimeInfo }
import org.apache.mesos.Protos._
import org.apache.mesos._

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

class MesosTaskScheduler(
    val mesosMaster:                  String,
    override val taskRPC:             TaskRPC,
    override val dockerImageVersion:  String      = "latest",
    override val dockerImageRegistry: String      = "",
    val restrictedClusterNames:       Seq[String] = Seq("cpu-jupyter", "gpu-jupyter")
)(implicit val context: ExecutionContext, val loggerFactory: JMLoggerFactory)
  extends TaskScheduler
  with Logging
  with Scheduler {

  private final val lock = new ReentrantLock(true)
  private final val registerLatch = new CountDownLatch(1)
  private var isShutDown = false

  // mesos scheduler driver
  private[scheduler] var schedulerDriver: SchedulerDriver = _

  val tasks = new ConcurrentHashMap[TaskID, (Task[_ <: TaskResult, _ <: TaskParams], TaskTimeInfo)]()

  val resultingPromises = new ConcurrentHashMap[TaskID, Promise[TaskResult]]()

  override def submitTask[T <: TaskResult](task: Task[T, _ <: TaskParams]): Future[T] = {
    lock.lock()
    val resultF = {
      if (isShutDown) {
        Future.failed(new CortexException("Scheduler was already stopped"))
      } else {

        tasks.put(task.id, (task, TaskTimeInfo(task.id, new Date())))
        val resultP = Promise[T]()
        resultingPromises.put(task.id, resultP.asInstanceOf[Promise[TaskResult]])

        resultP.future
      }
    }
    lock.unlock()

    resultF
  }

  override def cancelTask(taskId: String): Unit = {
    lock.lock()
    if (isShutDown) {
      log.warn("Scheduler was already stopped")
    } else {

      log.info(s"Killing task $taskId")

      schedulerDriver.killTask(
        TaskID.newBuilder()
          .setValue(taskId).build()
      )
    }
    lock.unlock()
  }

  override def resourceOffers(driver: SchedulerDriver, offers: util.List[Offer]): Unit =
    tasks.synchronized {
      log.info(s"Got ${offers.size()} offers: " + offers.map { offer =>
        offer.getId.getValue ++ offer.getAttribute("cluster").fold("")(value => s" ($value)")
      }.mkString(", "))

      offers.sortBy { offer =>
        (
          offer.getResource("gpus"), // so that GPU offers will be consumed last
          offer.getResource("cpus") // so that smallest CPU offer consumed first
        )
      }.foreach { offer =>
        if (offer.getAttribute("cluster").exists(restrictedClusterNames.contains(_))) {
          driver.declineOffer(offer.getId)
          log.info(s"Declined offer: ${offer.getId.getValue}: cluster restricted: ${
            offer.getAttribute("cluster").getOrElse("impossibru")
          }")
        } else {
          val pendingTasks = tasks.values()
            .filter(_._1.getState == cortex.TaskState.Pending)
            .toList
            .sortBy(_._1.cpus).reverse // so that largest CPU demand is served first
          log.info(s"Number of pending tasks: ${pendingTasks.size}")

          val tasksToLaunch = doFirstFit(offer, pendingTasks)

          if (tasksToLaunch.isEmpty) {
            driver.declineOffer(offer.getId)
            log.info(s"Declined offer: ${offer.getId.getValue}: no matching tasks")
          } else {
            driver.launchTasks(
              Collections.singletonList(offer.getId),
              tasksToLaunch
            )
            log.info(s"Launched offer: ${offer.getId.getValue} Tasks: ${tasksToLaunch.size}")
          }
        }
      }
    }

  private def getReason(status: TaskStatus) = {
    // remove confusing messages. This status is deprecated and used as a dummy now.
    if (status.getReason == TaskStatus.Reason.REASON_COMMAND_EXECUTOR_FAILED) {
      ""
    } else {
      status.getReason
    }
  }

  override def statusUpdate(driver: SchedulerDriver, status: TaskStatus): Unit = tasks.synchronized {
    log.info("Status Updated. Task id" + status.getTaskId +
      " State: " + status.getState +
      " Reason: " + getReason(status) +
      " Message: " + status.getMessage)

    val taskId = status.getTaskId.getValue
    val (task, taskTimeInfo) = tasks.get(taskId)

    // Mesos doesn't guarantee sending a status exactly ones.
    // So we have to handle only the first status ignoring all others
    if (Option(task).isEmpty) {
      log.warn(s"Skipping status update for task id - $taskId, because most probably it's already handled")
    } else {
      status.getState match {

        case TaskState.TASK_RUNNING => {
          task.setState(cortex.TaskState.Running)
        }

        case TaskState.TASK_FINISHED => {
          val p = resultingPromises.get(taskId)

          def retrieveRawResults(): Either[Throwable, Array[Byte]] = {
            Try(taskRPC.getResults(task.taskPath)) match {
              case Success(res) => Right(res)
              case Failure(e) =>
                log.error(s"Failed to retrieve results from taskRPC, taskId - $taskId," +
                  s" path - ${task.taskPath}," +
                  s" exception - ${e.getMessage}")
                Left(e)
            }
          }

          def parseRawResults(rawResults: Array[Byte]): Either[Throwable, TaskResult] = {
            Try(task.parseResult(rawResults)) match {
              case Success(res) => Right(res)
              case Failure(e) =>
                log.error(s"Failed to parse task result $rawResults, exception - ${e.getMessage}")
                Left(e)
            }
          }

          val parsedResults = for {
            rawResults <- retrieveRawResults().right
            parsedResults <- parseRawResults(rawResults).right
          } yield parsedResults

          parsedResults match {
            case Left(e) => p.failure(e)
            case Right(taskResult) => p.success({
              taskResult.taskTimeInfo = taskTimeInfo.copy(completedAt = Some(new Date()))
              taskResult
            })
          }

          // cleanup
          taskCleanup(taskId)
        }

        case TaskState.TASK_DROPPED |
          TaskState.TASK_ERROR |
          TaskState.TASK_LOST |
          TaskState.TASK_GONE =>
          if (task.getAttempts > 0) {
            task.decreaseAttempts()
            task.setState(cortex.TaskState.Pending)
            tasks.put(task.id, (task, taskTimeInfo.copy(startedAt = None)))
            log.warn(s"Attempt of Task $taskId unsuccesful: " + status.getMessage)
          } else {
            handleTaskFailure(taskId, task, status)
            taskCleanup(taskId)
          }

        case TaskState.TASK_FAILED => {
          handleTaskFailure(taskId, task, status)
          taskCleanup(taskId)
        }

        case TaskState.TASK_KILLED => {
          resultingPromises(taskId).failure({
            new CortexException(s"Task $taskId is killed")
          })
          task.setState(cortex.TaskState.Killed)
          taskCleanup(taskId)
        }

        case taskState => log.warn(s"Unhandled task state: ${taskState.toString} for taskId: $taskId")
      }
    }
  }

  private def handleTaskFailure(
    taskId: TaskID,
    task:   Task[_, _],
    status: TaskStatus
  ) = {
    def retrieveRawFailureResults(): Either[Throwable, Array[Byte]] = {
      Try(taskRPC.getResults(task.taskPath)) match {
        case Success(res) => Right(res)
        case Failure(e) =>
          log.error(s"Failed to retrieve results from taskRPC, taskId - $taskId," +
            s" path - ${task.taskPath}," +
            s" exception - ${e.getMessage}")
          Left(e)
      }
    }

    def parseRawFailureResults(rawResults: Array[Byte]): Either[Throwable, FailedTask] = {
      Try(FailedTask.parseResult(rawResults)) match {
        case Success(res) => Right(res)
        case Failure(e) =>
          log.error(s"Failed to parse task result $rawResults, exception - ${e.getMessage}")
          Left(e)
      }
    }

    val parsedResults = for {
      rawResults <- retrieveRawFailureResults().right
      parsedFailureResults <- parseRawFailureResults(rawResults).right
    } yield parsedFailureResults

    val exception = parsedResults match {
      case Left(_) => new CortexException(s"Task $taskId failed with message: " + status.getMessage)
      case Right(FailedTask(ErrorType.SystemError, errorMessage, stackTrace, _)) =>
        SystemCortexException(errorMessage, stackTrace)
      case Right(FailedTask(ErrorType.UserError, errorMessage, stackTrace, Some(errorCode))) =>
        UserCortexException(errorCode, errorMessage, stackTrace)
      case Right(FailedTask(ErrorType.UserError, _, _, None)) =>
        new CortexException(s"Task $taskId failed with message: ${status.getMessage}. User error doesn't contain an error code.")
    }

    resultingPromises
      .get(taskId)
      .failure(exception)
  }

  private def taskCleanup(taskId: TaskID) = {
    tasks.remove(taskId)
    resultingPromises.remove(taskId)
  }

  def doFirstFit(offer: Offer, pendingTasks: List[(Task[_ <: TaskResult, _ <: TaskParams], TaskTimeInfo)]): List[TaskInfo] = {
    val toLaunch = ArrayBuffer[TaskInfo]()
    var offerCpus = offer.getResource("cpus")
    var offerGpus = offer.getResource("gpus")
    var offerMem = offer.getResource("mem")

    for ((task, taskTimeInfo) <- pendingTasks) {
      if (task.cpus <= offerCpus && task.memory <= offerMem && task.gpus <= offerGpus) {
        offerCpus -= task.cpus
        offerGpus -= task.gpus
        offerMem -= task.memory
        toLaunch.add(buildTaskInfo(task, offer.getSlaveId))
        task.setState(cortex.TaskState.Staging)
        tasks.put(task.id, (task, taskTimeInfo.copy(startedAt = Some(new Date()))))

        // store task parameters in taskRPC to make them accessible from dockerized task
        taskRPC.passParameters(task.taskPath, task.getSerializedParams)
      }
    }

    toLaunch.toList
  }

  private implicit class OfferExtensions(offer: Offer) {
    def getResource(name: String): Double =
      offer.getResourcesList
        .filter(_.getName == name)
        .map(_.getScalar.getValue)
        .lift(0)
        .getOrElse(0.0)

    def getAttribute(name: String): Option[String] =
      offer.getAttributesList
        .filter(_.getName == name)
        .map(_.getText.getValue)
        .lift(0)
  }

  private def buildTaskInfo(task: Task[_, _], targetSlave: SlaveID): TaskInfo = {
    val taskID = TaskID
      .newBuilder()
      .setValue(task.id)
      .build()

    val taskInfo = TaskInfo
      .newBuilder()
      .setName("cortex.task " + taskID.getValue)
      .setTaskId(taskID)
      .addResources(
        Resource
          .newBuilder()
          .setName("cpus")
          .setType(Value.Type.SCALAR)
          .setScalar(Value.Scalar.newBuilder().setValue(task.cpus))
      )
      .addResources(
        Resource
          .newBuilder()
          .setName("mem")
          .setType(Value.Type.SCALAR)
          .setScalar(Value.Scalar.newBuilder().setValue(task.memory))
      )

    if (task.gpus > 0) {
      taskInfo.addResources(
        Resource
          .newBuilder()
          .setName("gpus")
          .setType(Value.Type.SCALAR)
          .setScalar(Value.Scalar.newBuilder().setValue(task.gpus.toDouble))
      )
    }

    taskInfo.setContainer(buildContainerInfo(task))
      .setCommand(
        CommandInfo
          .newBuilder()
          .setShell(false)
          .addAllArguments(buildTaskCommand(task))
      )
      .setSlaveId(targetSlave)

    taskInfo.build()
  }

  private def buildContainerInfo(task: Task[_, _]) = {
    val containerInfoBuilder = ContainerInfo.newBuilder()

    containerInfoBuilder.setType(ContainerInfo.Type.MESOS)
    containerInfoBuilder.setMesos(
      ContainerInfo.MesosInfo
        .newBuilder
        .setImage(
          Image.newBuilder
            .setType(Image.Type.DOCKER)
            .setDocker(Image.Docker.newBuilder().setName(s"$dockerImageRegistry${task.dockerImage}:$dockerImageVersion"))
        )
    )

    containerInfoBuilder
  }

  private def buildTaskCommand(task: Task[_, _]) =
    task.command ++ taskRPC.getRpcDockerParams(task.taskPath)

  override def offerRescinded(driver: SchedulerDriver, offerId: OfferID): Unit =
    log.info("Offer Rescinded: " + offerId.getValue)

  override def disconnected(driver: SchedulerDriver): Unit =
    log.warn("Disconnected")

  override def reregistered(driver: SchedulerDriver, masterInfo: MasterInfo): Unit =
    log.info("Reregistered")

  override def slaveLost(driver: SchedulerDriver, slaveId: SlaveID): Unit =
    log.warn("Slave Lost: " + slaveId.getValue)

  override def error(driver: SchedulerDriver, message: String): Unit =
    log.warn("Error: " + message)

  override def frameworkMessage(
    driver:     SchedulerDriver,
    executorId: ExecutorID,
    slaveId:    SlaveID,
    data:       Array[Byte]
  ): Unit =
    log.info("Got framework message from executor: " + executorId.getValue + ", slaveId: " + slaveId.getValue)

  override def registered(
    driver:      SchedulerDriver,
    frameworkId: FrameworkID,
    masterInfo:  MasterInfo
  ): Unit = {
    log.info("Registered as framework ID " + frameworkId.getValue)
    this.schedulerDriver = driver
    markRegistered()
  }

  override def executorLost(
    driver:     SchedulerDriver,
    executorId: ExecutorID,
    slaveId:    SlaveID,
    status:     Int
  ): Unit = {
    log.warn("Executor lost: " + slaveId.getValue)
  }

  def start(): Unit = {

    val driver = createDriver(mesosMaster)

    synchronized {
      @volatile
      var error: Option[Exception] = None

      // We create a new thread that will block inside `mesosDriver.run`
      // until the scheduler exists
      new Thread("jm-mesos-driver") {
        setDaemon(true)

        override def run(): Unit = {
          try {
            val ret = driver.run()
            log.info("driver.run() returned with code " + ret)
            if (Option(ret).contains(Status.DRIVER_ABORTED)) {
              error = Some(new CortexException("Error starting driver, DRIVER_ABORTED"))
              markErr()
            }
          } catch {
            case e: Exception =>
              log.error(s"driver.run() failed. ${e.getMessage}")
              error = Some(e)
              markErr()
          }
        }
      }.start()

      registerLatch.await()

      // in case of errors throw them in main thread
      error.foreach(throw _)
    }
  }

  override def stop(): Unit = {
    lock.lock()
    if (isShutDown) {
      log.warn("Scheduler was already stopped")
    } else {

      log.warn(s"Cancelling all tasks, id's: ${tasks.asScala.keys.toList.mkString(",")}")

      // stop driver and all it's tasks
      schedulerDriver.stop(false)

      // finalize all statuses and promises due to it won't be updated in [[statusUpdate]]
      tasks.foreach {
        case (_, (task, _)) =>
          task.setState(Killed)
      }
      tasks.clear()

      resultingPromises.asScala.foreach {
        case (_, promise) =>
          promise.failure(SchedulerStoppedCortexException())
      }

      isShutDown = true
    }
    lock.unlock()
  }

  private def createDriver(mesosMaster: String) = {
    val frameworkInfo = FrameworkInfo.newBuilder()
      .setUser("root") // typical for mesos frameworks (marathon, metronome etc). Tasks ara failing without this.
      .setName(s"Cortex Job Master - $dockerImageVersion")
      .addCapabilities(
        FrameworkInfo.Capability
          .newBuilder()
          .setType(FrameworkInfo.Capability.Type.GPU_RESOURCES)
      )
      .build()

    new MesosSchedulerDriver(
      this,
      frameworkInfo,
      mesosMaster
    )
  }

  /**
   * Signal that the scheduler has registered with Mesos.
   */
  private def markRegistered(): Unit = {
    registerLatch.countDown()
  }

  private def markErr(): Unit = {
    registerLatch.countDown()
  }
}
