package gemini.routes.jupyter

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.Credentials
import cortex.api.gemini.{ JupyterSessionRequest, JupyterSessionResponse, SessionStatus => ContractStatus }
import gemini.domain.jupyter.{ JupyterNodeParams, Session, SessionStatus }
import gemini.domain.remotestorage.S3TemporaryCredentials
import gemini.routes.BaseRoutes
import gemini.services.jupyter.JupyterSessionService
import play.api.libs.json.Json

import scala.concurrent.duration._

class JupyterSessionRoutes(
  settings: JupyterSessionRoutes.Settings,
  jupyterSessionService: JupyterSessionService
) extends BaseRoutes {

  val routes: Route =
    pathPrefix("sessions") {
      concat(
        (pathEnd & authenticateWithBasic & post & entity(as[JupyterSessionRequest])) { request =>
          val tempCredentials = S3TemporaryCredentials(
            region = request.awsRegion,
            bucketName = request.bucketName,
            accessKey = request.awsAccessKey,
            secretKey = request.awsSecretKey,
            sessionToken = request.awsSessionToken
          )
          onSuccess(
            jupyterSessionService.create(
              temporaryCredentials = tempCredentials,
              folderPath = request.projectPath,
              userAuthToken = request.userAccessToken,
              nodeParams = JupyterNodeParams(
                request.nodeParams.numberOfCpus,
                request.nodeParams.numberOfGpus
              )
            )
          ) { session =>
            complete(StatusCodes.Created -> buildJupyterSessionResponse(session))
          }
        },
        pathPrefix(JavaUUID) { id =>
          concat(
            authenticateWithBasic {
              concat(
                path("status") {
                  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
                  onSuccess(jupyterSessionService.getStatusesSource(id)) {
                    case Some(source) =>
                      complete {
                        source
                          .map { status =>
                            ServerSentEvent(
                              data = Json.toJson(convertStatus(status)).toString,
                              eventType = Some("Status")
                            )
                          }
                          .keepAlive(5.seconds, () => ServerSentEvent(data = "", eventType = Some("Heartbeat")))
                      }
                    case None => notFound
                  }
                },
                pathEnd {
                  concat(
                    get {
                      onSuccess(jupyterSessionService.get(id)) {
                        case None =>
                          notFound
                        case Some(session) =>
                          complete(buildJupyterSessionResponse(session))
                      }
                    },
                    delete {
                      jupyterSessionService.stop(id)
                      complete(Json.obj())
                    }
                  )
                }
              )
            },
            authenticateWithBearer(id) {
              pathEnd {
                concat(
                  post {
                    jupyterSessionService.sendHeartbeat(id)
                    complete(Json.obj())
                  },
                  delete {
                    jupyterSessionService.stop(id)
                    complete(Json.obj())
                  }
                )
              }
            }
          )
        }
      )
    }

  private val notFound: StandardRoute = complete(errorResponse(StatusCodes.NotFound, "Session not found"))

  private val authenticateWithBasic: Directive0 =
    authenticateBasic("Gemini session management", basicAuthAuthenticator).flatMap(_ => pass)

  private def authenticateWithBearer(sessionId: UUID): Directive0 =
    extractCredentials.flatMap {
      case Some(creds) if creds.scheme.equalsIgnoreCase("Bearer") =>
        onSuccess(jupyterSessionService.authenticate(sessionId, creds.token)).flatMap {
          case true => pass
          case false => rejectUnauthenticated(AuthenticationFailedRejection.CredentialsRejected)
        }
      case _ =>
        rejectUnauthenticated(AuthenticationFailedRejection.CredentialsMissing)
    }

  private def basicAuthAuthenticator(credentials: Credentials): Option[Unit] =
    credentials match {
      case p @ Credentials.Provided(authUserName) =>
        if (authUserName == settings.basicAuthUsername && p.verify(settings.basicAuthPassword)) Some(())
        else None
      case _ => None
    }

  private def rejectUnauthenticated(cause: AuthenticationFailedRejection.Cause): Directive0 =
    reject(
      AuthenticationFailedRejection(
        cause,
        HttpChallenges.oAuth2("")
      )
    )

  private def convertStatus(status: SessionStatus): cortex.api.gemini.SessionStatus = status match {
    case SessionStatus.Submitted => ContractStatus.Submitted
    case SessionStatus.Queued => ContractStatus.Queued
    case SessionStatus.Running => ContractStatus.Running
    case SessionStatus.Completed => ContractStatus.Completed
    case SessionStatus.Failed => ContractStatus.Failed
  }

  private def buildJupyterSessionResponse(session: Session): JupyterSessionResponse =
    JupyterSessionResponse(
      id = session.id.toString,
      token = session.token,
      url = session.url,
      status = convertStatus(session.status),
      startedAt = session.startedAt
    )

}

object JupyterSessionRoutes {
  case class Settings(
    basicAuthUsername: String,
    basicAuthPassword: String
  )
}
