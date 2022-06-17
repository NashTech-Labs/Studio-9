package baile.routes.info

import akka.http.scaladsl.model.StatusCodes
import baile.routes.RoutesSpec
import baile.utils.json.CommonFormats.DurationFormat
import play.api.libs.json.JsObject

import scala.concurrent.duration.Duration

class InfoRoutesSpec extends RoutesSpec {

  val routes = new InfoRoutes().routes

  "GET /status endpoint" should {
    "return current status" in {
      Get("/status").check {
        status shouldBe StatusCodes.OK
        (responseAs[JsObject] \ "uptime").as[Duration].length should be > 0L
      }
    }
  }

  "GET /health endpoint" should {
    "return healthy flag" in {
      Get("/health").check {
        status shouldBe StatusCodes.OK
        assert((responseAs[JsObject] \ "ok").as[Boolean])
      }
    }
  }

}
