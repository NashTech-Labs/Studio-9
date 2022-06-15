package orion.rest.common

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{ BasicHttpCredentials, HttpChallenges, `WWW-Authenticate` }
import akka.http.scaladsl.server.{ Directives, Route }
import orion.common.json4s.DefaultJson4sSupport
import orion.testkit.rest.HttpEndpointBaseSpec

class ConfigUserPassAuthenticatorSpec extends HttpEndpointBaseSpec {

  // Fixtures
  val validUserName = "search-user"
  val validPassword = "search-user-password"
  val validCredentials = BasicHttpCredentials(validUserName, validPassword)
  val authenticationRealm = "Secured endpoint"

  trait Scope extends RouteScope with Directives with DefaultJson4sSupport {
    val authenticator = ConfigUserPassAuthenticator(validUserName, validPassword)

    val routes: Route = authenticateBasic(realm = authenticationRealm, authenticator.authenticate) { _ =>
      path("secured") {
        get {
          complete(StatusCodes.OK)
        }
      }
    }
  }

  "When sending a request to a secured endpoint, it" should {
    "pass requests with valid credentials" in new Scope {
      Get("/secured").withCredentials(validCredentials)
        .check {
          handled shouldBe true
        }
    }
    "reject requests with invalid credentials requests" in new Scope {
      val invalidCredentials = BasicHttpCredentials("invalidUserName", "invalidPassword")
      val result = Get("/secured").withCredentials(invalidCredentials).runSeal

      result.check {
        status shouldEqual StatusCodes.Unauthorized
        responseAsString shouldEqual "The supplied authentication is invalid"
        header[`WWW-Authenticate`].flatMap(_.challenges.headOption) should contain(HttpChallenges.basic(authenticationRealm))
      }
    }
    "reject requests without credentials" in new Scope {
      val result = Get("/secured").runSeal

      result.check {
        status shouldEqual StatusCodes.Unauthorized
        responseAsString shouldEqual "The resource requires authentication, which was not supplied with the request"
        header[`WWW-Authenticate`].flatMap(_.challenges.headOption) should contain(HttpChallenges.basic(authenticationRealm))
      }
    }
  }

}
