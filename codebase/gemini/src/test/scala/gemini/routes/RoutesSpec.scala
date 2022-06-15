package gemini.routes
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import gemini.BaseSpec
import play.api.libs.json.JsObject

trait RoutesSpec extends BaseSpec with PlayJsonSupport { self =>

  trait RoutesSetup {

    val routes: Route

    implicit class ExtendedHttpRequest(request: HttpRequest) {

      def withQuery(query: Uri.Query): HttpRequest =
        request.copy(uri = request.uri.withQuery(query))

      def withQuery(query: Map[String, String]): HttpRequest =
        withQuery(Uri.Query(query))

      def withQuery(query: (String, String)*): HttpRequest =
        withQuery(query.toMap)

      def withCredentials(credentials: HttpCredentials): HttpRequest =
        request ~> addCredentials(credentials)

      def check[T](body: => T): T =
        check(ContentTypes.`application/json`)(body)

      def check[T](expectedContentType: ContentType)(body: => T): T =
        request ~> handledRoutes ~> self.check {
          contentType shouldBe expectedContentType
          body
        }

      def signed(implicit creds: HttpCredentials): HttpRequest = withCredentials(creds)

      def run(): RouteTestResult =
        request ~> handledRoutes ~> runRoute

      def runSeal(): RouteTestResult =
        request ~> Route.seal(handledRoutes) ~> runRoute

      private val handledRoutes = BaseRoutes.seal(conf)(routes)

    }

    protected def validateErrorResponse(response: HttpResponse): Unit = {
      responseAs[JsObject].keys should contain allElementsOf List("code", "message")
    }

  }

}
