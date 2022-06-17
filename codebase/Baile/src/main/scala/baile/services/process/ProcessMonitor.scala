package baile.services.process

import java.time.Instant
import java.util.UUID

import akka.actor.{ Actor, ActorLogging, Cancellable, PoisonPill, Props }
import akka.event.LoggingAdapter
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import baile.dao.process.ProcessDao
import baile.dao.process.ProcessDao._
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.job.{ CortexJobProgress, CortexJobStatus, CortexJobTerminalStatus }
import baile.domain.process.ProcessStatus._
import baile.domain.process.{ Process, ProcessStatus, ResultHandlerMeta }
import baile.services.cortex.datacontract.CortexErrorDetails
import baile.services.cortex.job.{ CortexJobService, JobLogging }
import baile.services.process.JobResultHandler.{ HandleException, HandleJobResult, HandlerMessage }
import baile.services.process.ProcessMonitor._
import baile.utils.ThrowableExtensions._
import baile.utils.TryExtensions._
import play.api.libs.json._

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class ProcessMonitor private[process](
  val processDao: ProcessDao,
  val cortexJobService: CortexJobService,
  readStoredProcesses: Boolean,
  private val processCheckInterval: FiniteDuration,
  private val startupRequestTimeout: FiniteDuration,
  private val handlersDependencySources: AnyRef*
) extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val jobResultHandleTimeout: Timeout = Timeout(2.minutes)
  implicit val logger: LoggingAdapter = log

  var processes: Map[String, WithId[Process]] = Map.empty
  var processCheckCancellable: Option[Cancellable] = None

  override def preStart(): Unit = {
    super.preStart()
    log.info("Loading current processes from storage")
    processes = Await.result(getCurrentProcesses, startupRequestTimeout).map(withId => withId.id -> withId).toMap
    log.info("Loaded current processes from storage")
    processCheckCancellable = Some(
      context.system.scheduler.schedule(processCheckInterval, processCheckInterval, self, CheckAllProcesses)
    )
  }

  override def postStop(): Unit = {
    processCheckCancellable.foreach(_.cancel())
    super.postStop()
  }

  override def receive: Receive = {
    case StartProcess(jobId, ownerId, authToken, targetId, targetType, handlerClass, meta) =>
      startProcess(
        jobId,
        ownerId,
        authToken,
        targetId,
        targetType,
        handlerClass,
        meta
      ) pipeTo sender
    case AddHandlerToProcess(processId, handlerClass, meta) =>
      addHandlerToProcess(processId, handlerClass, meta) pipeTo sender
    case CheckAllProcesses =>
      checkAllProcesses()
    case GetProcessById(id) =>
      sender ! getProcess(id)
    case GetProcessByTargetAndHandler(targetId, targetType, handlerClass) =>
      sender ! getProcess(targetId, targetType, handlerClass)
    case GetProcessesByTargetAndHandler(targetId, targetType, handlerClass) =>
      sender ! getProcesses(targetId, targetType, handlerClass)
    case GetProcessByAuthToken(token) =>
      sender ! getProcessByAuthToken(token)
    case CancelProcess(id) =>
      cancelProcess(id) pipeTo sender
  }

  private def getCurrentProcesses: Future[Seq[WithId[Process]]] = {
    if (readStoredProcesses) processDao.listAll(StatusIs(Queued) || StatusIs(Running))
    else Future.successful(Seq.empty)
  }

  private def startProcess[H <: JobResultHandler[_]](
    jobId: UUID,
    ownerId: UUID,
    ownerToken: Option[String],
    targetId: String,
    targetType: AssetType,
    handlerClass: Class[H],
    meta: JsObject
  ): Future[WithId[Process]] = {
    val process = Process(
      targetId = targetId,
      targetType = targetType,
      ownerId = ownerId,
      authToken = ownerToken,
      jobId = jobId,
      status = ProcessStatus.Queued,
      progress = None,
      estimatedTimeRemaining = None,
      created = Instant.now,
      started = None,
      completed = None,
      errorCauseMessage = None,
      errorDetails = None,
      onComplete = ResultHandlerMeta(
        handlerClassName = handlerClass.getCanonicalName,
        meta = meta
      ),
      auxiliaryOnComplete = Seq.empty
    )

    processDao.create(process).map { processId =>
      val withId = WithId(process, processId)
      processes += (processId -> withId)
      withId
    }
  }

  private def addHandlerToProcess[H <: JobResultHandler[_]](
    processId: String,
    handlerClass: Class[H],
    meta: JsObject
  ): Future[Boolean] = {
    val resultHandlerMeta = ResultHandlerMeta(handlerClass.getCanonicalName, meta)
    processes.get(processId) match {
      case Some(WithId(process, id)) =>
        processes = processes.updated(
          id,
          WithId(process.copy(auxiliaryOnComplete = process.auxiliaryOnComplete :+ resultHandlerMeta), id)
        )
        processDao.addHandler(processId, resultHandlerMeta).map(_ => true)
      case None =>
        Future.successful(false)
    }
  }

  private def checkAllProcesses(): Iterable[Future[Unit]] =
    processes.values.map(checkProcess)

  private def getProcess[H <: JobResultHandler[_]](
    targetId: String,
    targetType: AssetType,
    handlerClass: Option[Class[H]]
  ): Option[WithId[Process]] =
    getProcesses[H](targetId, targetType, handlerClass)
      .sortBy(_.entity.created)((left, right) => right compareTo left)
      .headOption

  private def getProcesses[H <: JobResultHandler[_]](
    targetId: String,
    targetType: AssetType,
    handlerClass: Option[Class[H]]
  ): Seq[WithId[Process]] =
    processes
      .values
      .toSeq
      .collect {
        case res@WithId(process, _) if
          process.targetId == targetId &&
          process.targetType == targetType &&
          handlerClass.fold(true)(_.getCanonicalName == process.onComplete.handlerClassName)
        => res
      }

  private def getProcess(id: String): Option[WithId[Process]] =
    processes.get(id)

  private def getProcessByAuthToken(token: String): Option[WithId[Process]] =
    processes.collectFirst { case (_, process) if process.entity.authToken.contains(token) => process }

  private def cancelProcess(id: String): Future[Unit] =
    processes.get(id) match {
      case None => Future.unit
      case Some(process) => cortexJobService.cancelJob(process.entity.jobId)
    }

  // scalastyle:off method.length
  private def checkProcess(processWithId: WithId[Process]): Future[Unit] = {

    val process = processWithId.entity

    def getCortexErrorCauseMessage(cortexErrorDetails: CortexErrorDetails): String = {
      cortexErrorDetails.errorCode match {
        case msg => s"[$msg]: Job failed on cortex side"
      }
    }

    def updateProcess(jobProgress: CortexJobProgress): Future[Unit] = {
      def updater(old: Process): Process = {
        val newStatus = ProcessStatus(jobProgress.status)
        old.copy(
          status = newStatus,
          progress = jobProgress.status match {
            case CortexJobStatus.Completed => Some(1)
            case _ => jobProgress.progress
          },
          estimatedTimeRemaining = jobProgress.estimatedTimeRemaining,
          errorCauseMessage = jobProgress.status match {
            case CortexJobStatus.Failed => jobProgress.cortexErrorDetails.map(getCortexErrorCauseMessage)
            // keep old error cause
            case _ => old.errorCauseMessage
          },
          errorDetails = jobProgress.status match {
            case CortexJobStatus.Failed => Some(jobProgress.cortexErrorDetails.mkString(","))
            // keep old error cause
            case _ => old.errorCauseMessage
          },
          started = if (newStatus == ProcessStatus.Running) Some(old.started.getOrElse(Instant.now)) else old.started,
          completed = jobProgress.status match {
            // keep old completed if already filled
            case _: CortexJobTerminalStatus => Some(old.completed.getOrElse(Instant.now))
            // reset to None if status is not terminal
            case _ => None
          }
        )
      }

      processes.get(processWithId.id).foreach { _ =>
        processes = processes.updated(processWithId.id, processWithId.copy(entity = updater(processWithId.entity)))
      }
      processDao.update(processWithId.id, updater).map(_ => ())
    }

    def runAuxiliaryHandler(lastStatus: CortexJobTerminalStatus, resultHandlerMeta: ResultHandlerMeta): Future[Any] = {
      val result = askHandler(
        resultHandlerMeta.handlerClassName,
        HandleJobResult(process.jobId, lastStatus, resultHandlerMeta.meta)
      )

      result.recoverWith {
        case error =>
          val errorDetails = error.printInfo
          JobLogging.error(
            process.jobId,
            s"Auxiliary handler failed. Now launching exception handling. Error details: $errorDetails"
          )
          recoverHandlingException(process, resultHandlerMeta)
      }
    }

    val result =
      for {
        jobProgress <- cortexJobService.getJobProgress(process.jobId)
        _ <- jobProgress.status match {
          case lastStatus: CortexJobTerminalStatus =>
            processes -= processWithId.id
            val resultHandlerMeta = process.onComplete
            val mainHandlerResult = askHandler(
              resultHandlerMeta.handlerClassName,
              HandleJobResult(process.jobId, lastStatus, resultHandlerMeta.meta)
            )
            mainHandlerResult.onComplete {
              case Success(_) => process.auxiliaryOnComplete.map { resultHandlerMeta =>
                runAuxiliaryHandler(lastStatus, resultHandlerMeta)
              }
              case Failure(error) =>
                val errorDetails = error.printInfo

                JobLogging.error(
                  process.jobId,
                  "Failed to handle job result." +
                    s"Updating process in storage and executing exception handlers. Error details: $errorDetails"
                )
                val mainExceptionHandlingResult = for {
                  _ <- processDao.update(
                    processWithId.id,
                    _.copy(
                      status = ProcessStatus.Failed,
                      errorCauseMessage = Some("Error handling job result"),
                      errorDetails = Some(errorDetails)
                    )
                  )
                  exceptionHandlingResult <- recoverHandlingException(process, process.onComplete)
                } yield exceptionHandlingResult

                mainExceptionHandlingResult.onComplete { _ =>
                  process.auxiliaryOnComplete.map { resultHandlerMeta =>
                    recoverHandlingException(process, resultHandlerMeta)
                  }
                }
            }
            mainHandlerResult.map(_ => ())
          case _ =>
            Future.unit
        }
      } yield jobProgress

    result.flatMap(updateProcess)
  }
  // scalastyle:on method.length

  private def recoverHandlingException(process: Process, resultHandlerMeta: ResultHandlerMeta): Future[Any] = {
    val result = askHandler(
      resultHandlerMeta.handlerClassName,
      HandleException(resultHandlerMeta.meta)
    )

    result.recover { case exceptionHandlingError =>
      JobLogging.error(
        process.jobId,
        s"Failed to handle job handling exception. Error details: ${ exceptionHandlingError.printInfo }"
      )
      throw exceptionHandlingError
    }
  }

  private def askHandler(handlerClassName: String, message: HandlerMessage): Future[Any] =
    for {
      handlerProps <- JobResultHandlerPropsFactory(handlerClassName, handlersDependencySources: _*).toFuture
      handler <- Try(context.actorOf(handlerProps)).toFuture
      result <- handler ? message
      _ = handler ! PoisonPill
    } yield result

}

object ProcessMonitor {

  private[process] case class StartProcess[H <: JobResultHandler[_]](
    jobId: UUID,
    ownerId: UUID,
    authToken: Option[String],
    targetId: String,
    targetType: AssetType,
    handlerClass: Class[H],
    meta: JsObject
  )

  private[process] case class AddHandlerToProcess[H <: JobResultHandler[_]](
    processId: String,
    handlerClass: Class[H],
    meta: JsObject
  )

  private[process] sealed trait GetProcessRequest

  private[process] case class GetProcessById(id: String) extends GetProcessRequest

  private[process] case class GetProcessByTargetAndHandler[H <: JobResultHandler[_]](
    targetId: String,
    targetType: AssetType,
    handlerClass: Option[Class[H]]
  ) extends GetProcessRequest

  private[process] case class GetProcessesByTargetAndHandler[H <: JobResultHandler[_]](
    targetId: String,
    targetType: AssetType,
    handlerClass: Option[Class[H]]
  )

  private[process] case class GetProcessByAuthToken(token: String) extends GetProcessRequest

  private[process] case class CancelProcess(id: String)

  def props(
    processDao: ProcessDao,
    cortexJobService: CortexJobService,
    readStoredProcesses: Boolean,
    processCheckInterval: FiniteDuration,
    startupRequestTimeout: FiniteDuration,
    handlersDependencySources: AnyRef*
  )(implicit ec: ExecutionContext): Props =
    Props(new ProcessMonitor(
      processDao,
      cortexJobService,
      readStoredProcesses,
      processCheckInterval,
      startupRequestTimeout,
      handlersDependencySources: _*
    ))

  private case object CheckAllProcesses

}
