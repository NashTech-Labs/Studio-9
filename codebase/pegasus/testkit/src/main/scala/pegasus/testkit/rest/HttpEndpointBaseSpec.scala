package pegasus.testkit.rest

import java.util.Date

import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.{ HttpRequest, Uri }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import pegasus.common.json4s.Json4sSupport
import pegasus.common.rest.marshalling.Json4sHttpSupport
import org.scalactic.Equality
import org.scalatest.{ Matchers, WordSpecLike }

trait HttpEndpointBaseSpec extends WordSpecLike with Matchers with ScalatestRouteTest with CustomEqualities { self =>

  val testAppConfig = new TestConfig()

  trait RouteScope extends Json4sHttpSupport { this: Json4sSupport =>
    val routes: Route

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

      def check[T](body: => T): T = {
        request ~> routes ~> self.check(body)
      }

      def run(): RouteTestResult = {
        request ~> routes ~> runRoute
      }

      def runSeal(): RouteTestResult = {
        request ~> Route.seal(routes) ~> runRoute
      }

    }

    implicit class ExtendedTestResult(result: RouteTestResult) {

      def check[T](body: => T): T = {
        self.check(body)(result)
      }

    }
  }

  def responseAsString: String = {
    // Need to import Predefined Unmarshallers here for handling String
    import akka.http.scaladsl.unmarshalling.Unmarshaller._
    responseAs[String]
  }
}

trait CustomEqualities {
  implicit val javaDateEquality =
    new Equality[Date] {
      def areEqual(a: Date, b: Any): Boolean =
        b match {
          case b: Date => a == b
          case _       => false
        }
    }
}
