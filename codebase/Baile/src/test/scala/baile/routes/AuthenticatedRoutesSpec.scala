package baile.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import baile.services.common.AuthenticationService

class AuthenticatedRoutesSpec extends ExtendedRoutesSpec {

  trait Setup extends RoutesSetup { setup =>

    val routes: Route = new AuthenticatedRoutes {
      override val authenticationService: AuthenticationService = setup.authenticationService

      val routes: Route =
        authenticatedWithBasicCredentials { authParams =>
          path("base-auth") {
            complete(authParams.user.id)
          }
        }
    }.routes

    val password = "password"

    authenticationService.authenticate(userToken) isLenient()

  }


  "AuthenticatedRoutes#authenticatedWithBasicCredentials" should {

    "authenticate user if correct username and password are provided" in new Setup {
      authenticationService.authenticate(user.username, password) shouldReturn future(Some(user))
      Get("/base-auth")
        .withCredentials(BasicHttpCredentials(user.username, password))
        .check {
          status shouldBe StatusCodes.OK
          responseAs[String] shouldBe user.id.toString
        }
    }

    "not authenticate user if username or password is incorrect" in new Setup {
      authenticationService.authenticate(user.username, password) shouldReturn future(None)
      Get("/base-auth")
        .withCredentials(BasicHttpCredentials(user.username, password))
        .check {
          status shouldBe StatusCodes.Unauthorized
        }
    }

    "authenticate user if correct token is provided" in new Setup {
      authenticationService.authenticate("", userToken) shouldReturn future(Some(user))
      Get("/base-auth")
        .withCredentials(BasicHttpCredentials("", userToken))
        .check {
          status shouldBe StatusCodes.OK
          responseAs[String] shouldBe user.id.toString
        }
    }

    "not authenticate user if token is incorrect" in new Setup {
      authenticationService.authenticate("", userToken) shouldReturn future(None)
      Get("/base-auth")
        .withCredentials(BasicHttpCredentials("", userToken))
        .check {
          status shouldBe StatusCodes.Unauthorized
        }
    }
  }

}
