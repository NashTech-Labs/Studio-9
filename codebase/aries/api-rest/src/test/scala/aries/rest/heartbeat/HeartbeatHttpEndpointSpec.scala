package aries.rest.heartbeat

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.testkit.TestProbe
import aries.common.json4s.AriesJson4sSupport
import aries.domain.rest.datacontracts.heartbeat.HeartbeatDataContract
import aries.domain.service._
import aries.domain.service.heartbeat.{ CreateHeartbeat, HeartbeatEntity }
import aries.testkit.BaseSpec
import aries.testkit.rest.HttpEndpointBaseSpec
import aries.testkit.service.DateSupportMocks

import scala.concurrent.duration._

class HeartbeatHttpEndpointSpec extends HttpEndpointBaseSpec with DateSupportMocks {

  // Fixtures
  val heartbeatId = UUID.randomUUID()
  val jobId = UUID.randomUUID()
  val nowDate = mockCurrentDate
  val progress = 1D
  val estimate: Option[FiniteDuration] = Some(2 hours)
  val heartbeatEntityCR = CreateHeartbeat(jobId, nowDate, progress, estimate)
  val heartbeatEntityResp = HeartbeatEntity(heartbeatId, jobId, nowDate, progress, estimate)
  val heartbeatDataContractCR = HeartbeatDataContract.CreateRequest(jobId, nowDate, progress, estimate)
  val heartbeatDataResp = HeartbeatDataContract.Response(heartbeatId, jobId, nowDate, progress, estimate)

  val nonExistentHeartbeatId = UUID.randomUUID()

  trait Scope extends RouteScope with AriesJson4sSupport {
    val heartbeatCommandService = TestProbe()
    val heartbeatQueryService = TestProbe()

    val heartbeatServiceRoute = new HeartbeatHttpEndpoint(
      heartbeatCommandService.ref,
      heartbeatQueryService.ref,
      testAppConfig
    )

    val routes = heartbeatServiceRoute.routes

  }

  "When sending a POST request, the Heartbeat API" should {
    "create a new Heartbeat and return a 201 Response if the request is valid" in new Scope {
      val result = Post("/heartbeats", heartbeatDataContractCR).run

      heartbeatCommandService.expectMsg(CreateEntity(heartbeatEntityCR))
      heartbeatCommandService.reply(heartbeatEntityResp)

      result.check {
        response.status shouldBe StatusCodes.Created
        responseAs[HeartbeatDataContract.Response] shouldBe heartbeatDataResp
      }
    }
    "not create a Heartbeat and return a 400 Response if the request is not valid" in new Scope {
      val emptyOwner = "" // Owner must not be empty
      val result = Post("/heartbeats", Map("owner" -> emptyOwner)).runSeal

      heartbeatCommandService.expectNoMsg()

      result.check {
        response.status shouldBe StatusCodes.BadRequest
      }
    }
  }
}

class HeartbeatHttpEndpointTranslatorsSpec extends BaseSpec with DateSupportMocks {
  import HeartbeatHttpEndpoint._

  val heartbeatDataContractCR = HeartbeatDataContract.CreateRequest(
    jobId                  = UUID.randomUUID(),
    created                = mockCurrentDate,
    currentProgress        = 1,
    estimatedTimeRemaining = Some(2 hours)
  )

  val heartbeatEntity = HeartbeatEntity(
    id                       = UUID.randomUUID(),
    job_id                   = UUID.randomUUID(),
    created_at               = mockCurrentDate,
    current_progress         = 1,
    estimated_time_remaining = Some(2 hours)
  )

  "HeartbeatDataContract.CreateRequest To CreateHeartbeat translator" should {
    "translate HeartbeatDataContract.CreateRequest to CreateHeartbeat" in {
      val result = toCreateHeartbeat.translate(heartbeatDataContractCR)
      result.job_id shouldBe heartbeatDataContractCR.jobId
      result.created_at shouldBe heartbeatDataContractCR.created
    }
  }

  "HeartbeatEntity To HeartbeatDataContract.Response translator" should {
    "translate HeartbeatEntity to HeartbeatDataContract.Response" in {
      val result = toHeartbeatDataContract.translate(heartbeatEntity)
      result.jobId shouldBe heartbeatEntity.job_id
      result.created shouldBe heartbeatEntity.created_at
    }
  }
}

