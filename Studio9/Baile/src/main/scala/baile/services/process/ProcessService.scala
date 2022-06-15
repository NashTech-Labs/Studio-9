package baile.services.process

import java.time.Instant
import java.util.UUID

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.util.Timeout
import baile.dao.process.ProcessDao
import baile.dao.process.ProcessDao._
import baile.daocommons.WithId
import baile.daocommons.filters.{ Filter, IdIs, TrueFilter }
import baile.daocommons.sorting.Direction.Descending
import baile.daocommons.sorting.{ Field, SortBy }
import baile.domain.asset.AssetType
import baile.domain.process.{ Process, ProcessStatus }
import baile.domain.usermanagement.User
import baile.services.common.EntityService
import baile.services.common.EntityService.WithSortByField
import baile.services.process.ProcessService.{ ProcessServiceError, _ }
import cats.data.EitherT
import cats.implicits._
import play.api.libs.json.{ Json, OWrites }

import scala.concurrent.{ ExecutionContext, Future }

class ProcessService(
  processMonitor: ActorRef,
  val dao: ProcessDao
)(
  implicit val ec: ExecutionContext,
  processMonitorAskTimeout: Timeout,
  val logger: LoggingAdapter
) extends EntityService[Process, ProcessServiceError]  with WithSortByField[Process, ProcessServiceError] {

  override val notFoundError: ProcessServiceError = ProcessNotFoundError
  override val sortingFieldNotFoundError: ProcessServiceError  = SortingFieldUnknown

  override val findField: String => Option[Field] = Map(
    "created" -> ProcessDao.Created,
    "started" -> ProcessDao.Started,
    "completed" -> ProcessDao.Completed
  ).get

  def getProcess(
    id: String
  ): Future[Either[ProcessServiceError, WithId[Process]]] =
    getProcess(ProcessMonitor.GetProcessById(id), IdIs(id))

  // TODO: Add a filter in place of targetType and handlerClass
  def getProcess[H <: JobResultHandler[_]](
    targetId: String,
    targetType: AssetType,
    handlerClass: Option[Class[H]]
  ): Future[Either[ProcessServiceError, WithId[Process]]] = {

    val basicFilter = TargetIdIs(targetId) && TargetTypeIs(targetType)
    val filter = handlerClass match {
      case Some(clazz) => basicFilter && HandlerClassNameIs(clazz.getCanonicalName)
      case None => basicFilter
    }
    getProcess(
      ProcessMonitor.GetProcessByTargetAndHandler[H](targetId, targetType, handlerClass),
      filter
    )
  }

  def getActiveProcessByAuthToken(
    ownerToken: String
  ): Future[Either[ProcessServiceError, WithId[Process]]] =
    getProcess(
      ProcessMonitor.GetProcessByAuthToken(ownerToken),
      AuthTokenIs(ownerToken) && StatusIs(ProcessStatus.Running)
    )

  def cancelProcess(
    id: String
  )(implicit user: User): Future[Either[ProcessServiceError, Unit]] = {
    val result = for {
      processOption <- EitherT.right(getProcessFromMonitor(ProcessMonitor.GetProcessById(id)))
      process <- EitherT.fromOption[Future](processOption, ProcessNotFoundError)
      cancelResult <- EitherT(cancelProcess(process))
    } yield cancelResult

    result.value
  }

  def cancelProcesses(
    targetId: String,
    targetType: AssetType
  )(implicit user: User): Future[Either[ProcessServiceError, Unit]] = {
    type EitherTFutureError[R] = EitherT[Future, ProcessServiceError, R]
    getProcessesFromMonitor(ProcessMonitor.GetProcessesByTargetAndHandler(
      targetId,
      targetType,
      None
    )).flatMap { processes =>
      processes
        .filter(_.entity.completed.isEmpty) // cancelling only incomplete processes
        .toList
        .traverse[EitherTFutureError, Unit](process => EitherT(cancelProcess(process)))
        .map(_ => ())
        .value
    }
  }

  def list(
    orderBy: Seq[String],
    page: Int,
    pageSize: Int,
    handlerClasses: Option[Seq[String]],
    processStarted: Option[Seq[Instant]],
    processCompleted: Option[Seq[Instant]]
  )(implicit user: User): Future[Either[ProcessServiceError, (Seq[WithId[Process]], Int)]] = {

    val canReadFilter = OwnerIdIs(user.id)
    val handlerClassFilter = handlerClasses match {
      case Some(classes) => HandlerClassNameIn(classes)
      case None => TrueFilter
    }

    def getProcessTimeFilter[T <: Filter](
      processTime: Option[Seq[Instant]],
      error: InvalidRangeProvided,
      filter: (Instant, Instant) => T
    ): Either[ProcessServiceError, Filter] = {
      processTime match {
        case Some(value) => value match{
          case timeFrom :: timeTo :: Nil => Right(filter(timeFrom, timeTo))
          case _ => Left(error)
        }
        case None => Right(TrueFilter)
      }
    }

    val result = for {
      processStartTimeFilter <- EitherT.fromEither[Future](getProcessTimeFilter(
        processStarted,
        InvalidRangeProvided("Range of time from and time to needs to be provided for Process Started."),
        ProcessStartedBetween
      ))
      processCompleteTimeFilter <- EitherT.fromEither[Future](getProcessTimeFilter(
        processCompleted,
        InvalidRangeProvided("Range of time from and time to needs to be provided for Process Completed."),
        ProcessCompletedBetween
      ))
      items <- EitherT(list(
        canReadFilter && handlerClassFilter && processStartTimeFilter && processCompleteTimeFilter,
        orderBy,
        page,
        pageSize
      ))
    } yield items

    result.value
  }

  private[services] def startProcess[M: OWrites, H <: JobResultHandler[M]](
    jobId: UUID,
    targetId: String,
    targetType: AssetType,
    handlerClass: Class[H],
    meta: M,
    userId: UUID,
    authToken: Option[String] = None
  ): Future[WithId[Process]] =
    (processMonitor ? ProcessMonitor.StartProcess(
      jobId,
      userId,
      authToken,
      targetId,
      targetType,
      handlerClass,
      Json.toJsObject(meta)
    )).mapTo[WithId[Process]]

  private[services] def addHandlerToProcess[M: OWrites, H <: JobResultHandler[M]](
    processId: String,
    handlerClass: Class[H],
    meta: M
  ): Future[Boolean] =
    (processMonitor ? ProcessMonitor.AddHandlerToProcess(
      processId,
      handlerClass,
      Json.toJsObject(meta)
    )).mapTo[Boolean]

  // TODO Keep signature in sync with getProcess
  // (i.e. also change param type to Filter once same gets done to getProcess)
  private[services] def getProcessMandatory[H <: JobResultHandler[_]](
    targetId: String,
    targetType: AssetType,
    handlerClass: Option[Class[H]]
  ): Future[WithId[Process]] =
    getProcess(targetId, targetType, handlerClass).map(_.getOrElse(
      throw new RuntimeException("Not found process with search params:" +
        s"targetId: $targetId; targetType: $targetType; handlerClass: $handlerClass"
      )
    ))

  private def getProcess(
    monitorMessage: ProcessMonitor.GetProcessRequest,
    filter: Filter
  ): Future[Either[ProcessService.ProcessServiceError, WithId[Process]]] =
    for {
      fromMonitorResult <- getProcessFromMonitor(monitorMessage)
      fromStorageResult <- {
        fromMonitorResult match {
          case Some(process) => Future.successful(Some(process))
          case None => getProcessFromStorage(filter)
        }
      }
    } yield fromStorageResult match {
      case Some(process) => process.asRight
      case None => ProcessNotFoundError.asLeft
    }

  private def cancelProcess(
    process: WithId[Process]
  )(implicit user: User): Future[Either[ProcessServiceError, Unit]] = {
    val result = for {
      _ <- EitherT.cond[Future](process.entity.ownerId == user.id, (), ActionForbiddenError)
      _ <- EitherT.right[ProcessServiceError](processMonitor ? ProcessMonitor.CancelProcess(process.id))
    } yield ()

    result.value
  }

  private def getProcessFromMonitor(
    monitorMessage: ProcessMonitor.GetProcessRequest
  ): Future[Option[WithId[Process]]] =
    (processMonitor ? monitorMessage).mapTo[Option[WithId[Process]]]

  private def getProcessesFromMonitor(
    monitorMessage: ProcessMonitor.GetProcessesByTargetAndHandler[JobResultHandler[_]]
  ): Future[Seq[WithId[Process]]] =
    (processMonitor ? monitorMessage).mapTo[Seq[WithId[Process]]]

  private def getProcessFromStorage(filter: Filter): Future[Option[WithId[Process]]] =
    dao.list(
      filter = filter,
      pageSize = 1,
      pageNumber = 1,
      sortBy = Some(SortBy(Created, Descending))
    ).map(_.headOption)

}

object ProcessService {

  sealed trait ProcessServiceError

  case object ProcessNotFoundError extends ProcessServiceError

  case object ActionForbiddenError extends ProcessServiceError

  case object SortingFieldUnknown extends ProcessServiceError

  case class InvalidRangeProvided(message: String) extends ProcessServiceError

}
