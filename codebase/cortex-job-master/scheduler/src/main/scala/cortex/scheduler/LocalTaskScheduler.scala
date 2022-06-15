package cortex.scheduler

import java.util.Date
import java.util.concurrent.{ CancellationException, ConcurrentHashMap }

import cortex.common.Logging
import cortex.common.future.CancellableFuture
import cortex.common.logging.JMLoggerFactory
import cortex.rpc.TaskRPC
import cortex._

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.sys.process.Process
import scala.util.control.NonFatal

class LocalTaskScheduler(
    override val taskRPC:             TaskRPC,
    override val dockerImageVersion:  String  = "latest",
    override val dockerImageRegistry: String  = ""
)(implicit val context: ExecutionContext, val loggerFactory: JMLoggerFactory)
  extends TaskScheduler
  with Logging {
  // we need a pool of resources  - number of executors. At the moment there should be
  // no more running tasks than numExecutors

  val tasks = new ConcurrentHashMap[TaskID, CancellableFuture[Array[Byte]]]()

  override def submitTask[T <: TaskResult](task: Task[T, _ <: TaskParams]): Future[T] = {
    log.info(s"Submitting task with ${task.id}")

    def submitTaskInternal(task: Task[T, _]): Future[T] = {
      val submittedAt = new Date()
      val cmd = Seq(
        "docker",
        "run",
        "--rm", // remove container after process ends
        "--net=host", // to make local hdfs visible from container
        "-e CUDA_VISIBLE_DEVICES=\"\"", // do not use GPU for local scheduler
        s"$dockerImageRegistry${task.dockerImage}:$dockerImageVersion"
      ) ++
        task.command ++
        taskRPC.getRpcDockerParams(task.taskPath) // rpc parameters, so task can get parameters and store results

      // store task parameters in taskRPC to make them accessible from dockerized task
      taskRPC.passParameters(task.taskPath, task.getSerializedParams)

      // run command
      val startedAt = new Date()
      val rawResult = runCommand(task, cmd, task.getAttempts)

      // parse result on complete
      rawResult.map(bytes => {
        val result = task.parseResult(bytes)
        result.taskTimeInfo = TaskTimeInfo(task.id, submittedAt, Some(startedAt), Some(new Date()))

        result
      })
    }

    submitTaskInternal(task).recoverWith {
      case e: CancellationException =>
        log.info(s"Cancelled task ${task.id}")
        throw new CortexException(e.getMessage)

      case NonFatal(e) =>
        if (task.getAttempts > 0) {
          task.decreaseAttempts()
          log.info(s"Retrying task ${task.id} with, remaining attempts ${task.getAttempts}, exception - ${e.getMessage}")
          submitTask(task)
        } else {
          log.info(s"Failed task - ${task.id}, exception - ${e.getMessage}")
          e.printStackTrace()
          throw e
        }

      case e =>
        log.info(s"Failed task - ${task.id}, exception - ${e.getMessage}")
        e.printStackTrace()
        throw e
    }
  }

  private def runCommand[T <: TaskResult](
    task:     Task[_ <: TaskResult, _],
    command:  Seq[String],
    attempts: Int
  ): Future[Array[Byte]] = {
    val cancellableFuture = CancellableFuture {
      val process = Process(command)
      process.! // blocking call to run process

      // get result that was stored in taskRPC by dockerized task
      taskRPC.getResults(task.taskPath)
    }
    tasks.put(task.id, cancellableFuture)
    cancellableFuture.future
  }

  override def cancelTask(taskId: String): Unit = this.synchronized {
    //TODO set state of the task to killed
    Option(tasks.get(taskId)) match {
      case Some(task) =>
        log.info(s"Cancelling task $task")
        task.cancel()
        tasks.remove(taskId)
      case None =>
        log.warn(s"Task with id: $taskId doesn't exist")
    }
  }

  override def stop(): Unit = {
    tasks.asScala.foreach {
      case (_, task) =>
        task.cancel()
    }
  }
}
