package orion.common.rest.authentication

import akka.http.scaladsl.server.directives.SecurityDirectives.Authenticator

trait UserPassAuthenticator[T] {

  val authenticate: Authenticator[T]
}
