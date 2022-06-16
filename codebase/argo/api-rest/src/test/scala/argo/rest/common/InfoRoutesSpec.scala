package argo.rest.common

import akka.http.scaladsl.model.{ ContentTypes, StatusCodes }
import argo.domain.rest.status.{ About, HealthCheckResponse, Status }
import argo.testkit.rest.HttpEndpointBaseSpec

class InfoRoutesSpec extends HttpEndpointBaseSpec {

  val config = testAppConfig

  trait Scope extends RouteScope with InfoRoutes {
    val routes = infoRoutes(config)
  }

  "When sending a GET request to /health endpoint, it" should {
    "return a 200 Response containing a health-check information json body" in new Scope {
      Get("/health").check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[HealthCheckResponse] shouldBe HealthCheckResponse(true)
      }
    }
  }

  "When sending a GET request to /about endpoint, it" should {
    "return a 200 Response containing an about information json body" in new Scope {
      Get("/about").check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[About].serviceName shouldBe config.serviceConfig.name
      }
    }
  }

  "When sending a GET request to /status endpoint, it" should {
    "return a 200 Response containing a status information json body" in new Scope {
      Get("/status").check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[Status].serviceName shouldBe config.serviceConfig.name
        responseAs[Status].uptime should include("milliseconds")
      }
    }
  }

}
