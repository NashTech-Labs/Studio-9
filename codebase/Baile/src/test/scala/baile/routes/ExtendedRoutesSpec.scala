package baile.routes

import akka.http.scaladsl.model.headers.{ Authorization, GenericHttpCredentials, HttpCredentials }
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import baile.ExtendedBaseSpec
import baile.domain.usermanagement.User
import baile.services.common.AuthenticationService
import baile.services.usermanagement.util.TestData.SampleUser
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import play.api.libs.json.JsObject

trait ExtendedRoutesSpec extends ExtendedBaseSpec with PlayJsonSupport { self =>

  trait RoutesSetup {

    val routes: Route
    val authenticationService: AuthenticationService = mock[AuthenticationService](withSettings.lenient())

    def validateErrorResponse(response: JsObject): Unit =
      response.keys should contain allOf("code", "message")

    implicit val user: User = SampleUser
    val userToken = "token"
    authenticationService.authenticate(userToken) shouldReturn future(Some(SampleUser))

    implicit class ExtendedHttpRequest(request: HttpRequest) {

      def withQuery(query: Uri.Query): HttpRequest = {
        request.copy(uri = request.uri.withQuery(query))
      }

      def withQuery(query: Map[String, String]): HttpRequest = {
        withQuery(Uri.Query(query))
      }

      def withQuery(query: (String, String)*): HttpRequest = {
        withQuery(query.toMap)
      }

      def withCredentials(credentials: HttpCredentials): HttpRequest = {
        request ~> addCredentials(credentials)
      }

      def signed: HttpRequest = request.addHeader(
        Authorization(GenericHttpCredentials("Bearer", userToken))
      )

      def check[T](body: => T): T = {
        check(ContentTypes.`application/json`)(body)
      }

      def check[T](expectedContentType: ContentType)(body: => T): T = {
        request ~> handledRoutes ~> self.check {
          contentType shouldBe expectedContentType
          body
        }
      }

      def run(): RouteTestResult = {
        request ~> handledRoutes ~> runRoute
      }

      def runSeal(): RouteTestResult = {
        request ~> Route.seal(handledRoutes) ~> runRoute
      }

      private val handledRoutes = BaseRoutes.seal(conf)(routes)

    }

  }
}
