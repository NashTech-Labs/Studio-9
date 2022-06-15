package sqlserver.routes.info

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import sqlserver.domain.info.Status
import sqlserver.routes.RoutesSpec
import sqlserver.services.info.InfoService
import play.api.libs.json.JsObject
import sqlserver.utils.json.CommonFormats.DurationFormat

import scala.concurrent.duration.Duration

class InfoRoutesSpec extends RoutesSpec {

  trait Setup extends RoutesSetup {
    val service: InfoService = mock[InfoService]
    val routes: Route = new InfoRoutes(service).routes
  }

  "GET /status endpoint" should {

    "return current status" in new Setup {
      service.getUptime shouldReturn future(Status(Duration.fromNanos(100000L)))
      Get("/status").check {
        status shouldBe StatusCodes.OK
        (responseAs[JsObject] \ "uptime").as[Duration].length should be > 0L
      }
    }
  }

  "GET /health endpoint" should {
    "return healthy flag" in new Setup {
      Get("/health").check {
        status shouldBe StatusCodes.OK
        assert((responseAs[JsObject] \ "isAlive").as[Boolean])
      }
    }

  }

}
