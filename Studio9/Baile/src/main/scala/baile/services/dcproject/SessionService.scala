package baile.services.dcproject

import java.time.Instant

import akka.NotUsed
import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.Timeout
import baile.dao.asset.Filters.OwnerIdIs
import baile.dao.dcproject.SessionDao.DCProjectIdIs
import baile.dao.dcproject.{ DCProjectDao, SessionDao }
import baile.daocommons.WithId
import baile.daocommons.filters.IdIs
import baile.domain.dcproject._
import baile.domain.remotestorage.{ S3TemporaryCredentials, TemporaryCredentials }
import baile.domain.usermanagement.User
import baile.services.common.EntityUpdateFailedException
import baile.services.dcproject.SessionMonitor.SubscribeSession
import baile.services.dcproject.SessionService.SessionServiceError._
import baile.services.dcproject.SessionService.{ SessionNodeParams, SessionServiceError }
import baile.services.gemini.GeminiService
import baile.services.remotestorage.RemoteStorageService
import baile.utils.TryExtensions._
import cats.data.EitherT
import cats.implicits._
import cortex.api.gemini.SessionStatus.{ Completed, Failed, Queued, Running, Submitted }
import cortex.api.gemini.{ JupyterNodeParamsRequest, JupyterSessionRequest }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class SessionService(
  protected val dcProjectDao: DCProjectDao,
  protected val geminiService: GeminiService,
  protected val projectStorage: RemoteStorageService,
  protected val sessionDao: SessionDao,
  projectStorageKeyPrefix: String,
  sessionNodeParams: SessionNodeParams,
  sessionMonitor: ActorRef
)(
  implicit val ec: ExecutionContext,
  logger: LoggingAdapter,
  materializer: Materializer,
  sessionMonitorAskTimeout: Timeout
) {

  def create(
    projectId: String,
    accessToken: String,
    runOnGPUNode: Boolean
  )(implicit user: User): Future[Either[SessionServiceError, WithId[Session]]] = {

    def saveSession(
      geminiSessionId: String,
      geminiSessionToken: String,
      geminiSessionUrl: String,
      createdAt: Instant
    ): Future[WithId[Session]] = sessionDao.create { _ =>
      Session(
        geminiSessionId,
        geminiSessionToken,
        geminiSessionUrl,
        projectId,
        createdAt
      )
    }

    def buildSessionRequestParams(
      temporaryCredentials: TemporaryCredentials,
      projectPath: String
    ): Try[JupyterSessionRequest] = Try {
      // TODO Teach gemini contract to work with arbitrary TemporaryCredentials and refactor this method accordingly
      temporaryCredentials match {
        case s3AccessCredentials: S3TemporaryCredentials =>
          JupyterSessionRequest(
            userAccessToken = accessToken,
            awsRegion = s3AccessCredentials.region,
            awsAccessKey = s3AccessCredentials.accessKey,
            awsSecretKey = s3AccessCredentials.secretKey,
            awsSessionToken = s3AccessCredentials.sessionToken,
            bucketName = s3AccessCredentials.bucketName,
            projectPath = projectPath,
            nodeParams = {
              val numberOfGpus = if (runOnGPUNode) Some(sessionNodeParams.numberOfGpus) else None
              JupyterNodeParamsRequest(numberOfCpus = Some(sessionNodeParams.numberOfCpus), numberOfGpus = numberOfGpus)
            }
          )
        case credentials => throw new RuntimeException(s"Unsupported credentials $credentials")
      }
    }

    val result = for {
      project <- EitherT(getDCProject(projectId))
      _ <- EitherT.cond[Future](
        project.entity.status == DCProjectStatus.Idle,
        (),
        ProjectAlreadyInSession
      )
      projectPath <- EitherT.rightT[Future, SessionServiceError](projectStorage.path(
        projectStorageKeyPrefix,
        project.entity.basePath
      ))
      temporaryCredentials <- EitherT.right[SessionServiceError](projectStorage.getTemporaryCredentials(
        projectPath,
        user
      ))
      sessionRequest <- EitherT.right[SessionServiceError](buildSessionRequestParams(
        temporaryCredentials,
        projectPath
      ).toFuture)
      _ <- EitherT.right[SessionServiceError](updateDCProjectStatus(project.id, DCProjectStatus.Interactive))
      sessionResponse <- EitherT.right[SessionServiceError](geminiService.createSession(sessionRequest))
      session <- EitherT.right[SessionServiceError](saveSession(
        sessionResponse.id,
        sessionResponse.token,
        sessionResponse.url,
        sessionResponse.startedAt
      ))
      _ = sessionMonitor ! SubscribeSession(session)
    } yield session

    result.value
  }

  def get(projectId: String)(implicit user: User): Future[Either[SessionServiceError, WithId[Session]]] = {
    val result = for {
      project <- EitherT(getDCProject(projectId))
      session <- EitherT(getSession(project.id))
    } yield session

    result.value
  }

  def getStatusesSource(
    projectId: String
  )(implicit user: User): Future[Either[SessionServiceError, Source[SessionStatus, NotUsed]]] = {

    val result = for {
      project <- EitherT(getDCProject(projectId))
      _ <- EitherT.cond[Future](
        project.entity.status == DCProjectStatus.Interactive,
        (),
        ProjectNotInSession
      )
      session <- EitherT(getSession(projectId))
      sessionSource <- EitherT.right[SessionServiceError](
        geminiService.getSessionStatusesSource(session.entity.geminiSessionId)
      )
    } yield sessionSource.map {
      case Submitted => SessionStatus.Submitted
      case Queued => SessionStatus.Queued
      case Running => SessionStatus.Running
      case Completed => SessionStatus.Completed
      case Failed => SessionStatus.Failed
    }

    result.value
  }

  def cancel(
    projectId: String
  )(implicit user: User): Future[Either[SessionServiceError, Unit]] = {
    val result = for {
      project <- EitherT(getDCProject(projectId))
      _ <- EitherT(cancel(project))
    } yield ()

    result.value
  }

  private[services] def cancel(
    project: WithId[DCProject]
  )(implicit user: User): Future[Either[SessionServiceError, Unit]] = {
    val result = for {
      _ <- EitherT.cond[Future](
        project.entity.status == DCProjectStatus.Interactive,
        (),
        ProjectNotInSession
      )
      _ <- EitherT.right[SessionServiceError](handleSessionIfExists(project))
      _ <- EitherT.right[SessionServiceError](updateDCProjectStatus(project.id, DCProjectStatus.Idle))
    } yield ()

    result.value
  }

  private def handleSessionIfExists(project: WithId[DCProject]): Future[Unit] = {

    def cancelSessionIfExists(optionalSession: Option[WithId[Session]]): Future[Unit] = {
      optionalSession match {
        case Some(session) => for {
          _ <- geminiService.cancelSession(session.entity.geminiSessionId)
          _ <- sessionDao.delete(session.id)
        } yield ()
        case None => Future.unit
      }
    }

    for {
      session <- getSession(project.id)
      optionalSession = session.toOption
      _ <- cancelSessionIfExists(optionalSession)
    } yield ()
  }

  private def getDCProject(
    projectId: String
  )(implicit user: User): Future[Either[SessionServiceError, WithId[DCProject]]] = {
    dcProjectDao.get(IdIs(projectId) && OwnerIdIs(user.id)) map {
      Either.fromOption(_, DCProjectNotFound)
    }
  }

  private def getSession(
    projectId: String
  ): Future[Either[SessionServiceError, WithId[Session]]] = {
    sessionDao.get(DCProjectIdIs(projectId)) map {
      Either.fromOption(_, SessionNotFound)
    }
  }

  private def updateDCProjectStatus(projectId: String, status: DCProjectStatus): Future[WithId[DCProject]] = {
    dcProjectDao.update(projectId, _.copy(status = status)).map(
      _.getOrElse(throw EntityUpdateFailedException(projectId, classOf[DCProject]))
    )
  }

}

object SessionService {

  case class SessionNodeParams(numberOfCpus: Double, numberOfGpus: Double)

  sealed trait SessionServiceError

  object SessionServiceError {

    case object DCProjectNotFound extends SessionServiceError

    case object ProjectAlreadyInSession extends SessionServiceError

    case object ProjectNotInSession extends SessionServiceError

    case object SessionNotFound extends SessionServiceError

  }

}
