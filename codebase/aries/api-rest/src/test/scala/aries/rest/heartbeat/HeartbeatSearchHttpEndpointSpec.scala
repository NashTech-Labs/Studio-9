package aries.rest.heartbeat

import java.util.UUID

import akka.http.scaladsl.model.{ ContentTypes, StatusCodes }
import akka.testkit.TestProbe
import aries.common.json4s.AriesJson4sSupport
import aries.domain.rest.datacontracts.heartbeat.HeartbeatDataContract
import aries.domain.service.heartbeat.{ FindLatestHeartbeat, HeartbeatEntity }
import aries.testkit.BaseSpec
import aries.testkit.rest.HttpEndpointBaseSpec
import aries.testkit.service.DateSupportMocks

import scala.concurrent.duration.{ FiniteDuration, _ }

class HeartbeatSearchHttpEndpointSpec extends HttpEndpointBaseSpec with DateSupportMocks {

  // Fixtures
  val heartbeatId = UUID.randomUUID()
  val jobId = UUID.randomUUID()
  val nowDate = mockCurrentDate
  val progress = 1D
  val estimate = Some(2 hours)
  val heartbeatEntityResp = HeartbeatEntity(heartbeatId, jobId, nowDate, progress, estimate)
  val heartbeatDataResp = HeartbeatDataContract.Response(heartbeatId, jobId, nowDate, progress, estimate)

  trait Scope extends RouteScope with AriesJson4sSupport {
    val heartbeatQueryService = TestProbe()
    val heartbeatServiceRoute = new HeartbeatSearchHttpEndpoint(
      heartbeatQueryService.ref,
      testAppConfig
    )

    val routes = heartbeatServiceRoute.routes

  }

  "When sending a GET request to /heartbeats/latest endpoint, it" should {
    "find the latest heartbeat by jobId" in new Scope {
      val heartbeatLatestEndpoint = "/heartbeats/latest"

      val result = Get(heartbeatLatestEndpoint).withQuery("jobId" -> jobId.toString).run
      heartbeatQueryService.expectMsg(FindLatestHeartbeat(jobId))
      heartbeatQueryService.reply(Seq(heartbeatEntityResp))

      result.check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[Seq[HeartbeatDataContract.Response]] shouldBe Seq(heartbeatDataResp)
      }
    }
  }
}

class HeartbeatSearchHttpEndpointTranslatorsSpec extends BaseSpec {

  import HeartbeatSearchHttpEndpoint._

  val heartbeatDataContract = HeartbeatDataContract.SearchRequest(
    jobId = UUID.randomUUID()
  )

  "HeartbeatDataContract.SearchRequest to HeartbeatSearchCriteria translator" should {
    "translate HeartbeatDataContract.SearchRequest to HeartbeatSearchCriteria" in {
      val result = toHeartbeatSearchCriteria.translate(heartbeatDataContract)
      result.job_id shouldBe heartbeatDataContract.jobId
    }

    "translate HeartbeatDataContract.SearchRequest to try of HeartbeatSearchCriteria" in {
      val result = toHeartbeatSearchCriteria.tryToTranslate(heartbeatDataContract)
      result.isSuccess shouldBe true
    }
  }
}

