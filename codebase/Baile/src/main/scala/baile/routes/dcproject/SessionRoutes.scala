package baile.routes.dcproject

import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import baile.domain.usermanagement.User
import baile.routes.AuthenticatedRoutes
import baile.routes.contract.common.IdResponse._
import baile.routes.contract.common.{ ErrorResponse, IdResponse }
import baile.routes.contract.dcproject.{ ProjectSessionCreateRequest, ProjectSessionResponse, SessionStatusWrites }
import baile.services.common.AuthenticationService
import baile.services.dcproject.SessionService
import baile.services.dcproject.SessionService.SessionServiceError
import com.typesafe.config.Config

import scala.concurrent.duration._

class SessionRoutes(
  val conf: Config,
  val authenticationService: AuthenticationService,
  val service: SessionService
) extends AuthenticatedRoutes {

  val routes: Route = authenticated { authParams =>
    implicit val user: User = authParams.user
    pathPrefix("dc-projects") {
      pathPrefix(Segment) { dcProjectId =>
        pathPrefix("session") {
          pathEnd {
            get {
              onSuccess(service.get(dcProjectId)) {
                case Right(session) => complete(ProjectSessionResponse.fromDomain(session))
                case Left(error) => complete(translateError(error))
              }
            } ~
            (post & entity(as[ProjectSessionCreateRequest])) { request =>
              onSuccess(service.create(
                dcProjectId,
                authParams.token,
                request.useGPU
              )) {
                case Right(session) => complete(ProjectSessionResponse.fromDomain(session))
                case Left(error) => complete(translateError(error))
              }
            } ~
            delete {
              onSuccess(service.cancel(dcProjectId)) {
                case Right(_) => complete(IdResponse(dcProjectId))
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          path("status") {
            get {
              onSuccess(service.getStatusesSource(dcProjectId)) {
                case Right(source) =>
                  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
                  complete {
                    source
                      .map { status =>
                        ServerSentEvent(
                          data = SessionStatusWrites.writes(status).as[String]
                        )
                      }
                      .keepAlive(5.seconds, () => ServerSentEvent(data = "", eventType = Some("Heartbeat")))
                  }
                case Left(error) => complete(translateError(error))
              }
            }
          }
        }
      }
    }
  }

  private def translateError(error: SessionServiceError): (StatusCode, ErrorResponse) = {
    error match {
      case SessionServiceError.DCProjectNotFound =>
        errorResponse(StatusCodes.NotFound, "Project not found")
      case SessionServiceError.ProjectAlreadyInSession =>
        errorResponse(StatusCodes.BadRequest, "Project is already in a session")
      case SessionServiceError.ProjectNotInSession =>
        errorResponse(StatusCodes.BadRequest, "Project is not in a running session")
      case SessionServiceError.SessionNotFound =>
        errorResponse(StatusCodes.NotFound, "Project Session not found")
    }
  }

}
