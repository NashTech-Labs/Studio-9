package baile.services.common

import baile.domain.usermanagement.User
import baile.services.process.ProcessService
import baile.services.usermanagement.UmService

import scala.concurrent.{ ExecutionContext, Future }

class AuthenticationService(umService: UmService, processService: ProcessService)(implicit ec: ExecutionContext) {

  def authenticate(token: String): Future[Option[User]] =
    for {
      userResponse <- umService.validateAccessToken(token)
      result <- userResponse match {
        case Right(user) => Future.successful(Some(user))
        case Left(_) =>
          for {
            processResponse <- processService.getActiveProcessByAuthToken(token)
            result <- processResponse match {
              case Left(_) => Future.successful(None)
              case Right(process) =>
                umService.getUserMandatory(process.entity.ownerId)
                  .map { user =>
                    Some(user.toExperimentExecutor(process.entity.targetId))
                  }
            }
          } yield result
      }
    } yield result

  /** Authenticate by username/password pair.
   * If username is empty string, then interpret password as token.
   */
  def authenticate(username: String, password: String): Future[Option[User]] =
    if (username.nonEmpty) {
      for {
        response <- umService.signIn(username, password)
        authParameters <- response match {
          case Right(accessToken) => authenticate(accessToken.token)
          case Left(_) => Future.successful(None)
        }
      } yield authParameters
    } else {
      authenticate(password)
    }

}
