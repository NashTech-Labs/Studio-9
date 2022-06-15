package pegasus

import java.time.{ ZoneId, ZonedDateTime }

import com.spingo.op_rabbit.SubscriptionRef
import cortex.api.pegasus.{ CreatedBy, PegasusJobStatus, PredictionImportRequest, PredictionImportResponse }
import taurus.pegasus.{ OrionIpcProvider, PegasusService }
import taurus.testkit.service.ServiceBaseSpec
import akka.pattern.ask
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{ Seconds, Span }
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.concurrent.ScalaFutures._

import scala.concurrent.ExecutionContext

class PegasusServiceUnitSpec extends ServiceBaseSpec {
  //scalastyle:off
  val timeoutConfig = PatienceConfiguration.Timeout(Span(5, Seconds))
  //scalastyle:on

  trait Scope {
    val orionIpcProvider = new OrionIpcProvider(system) {
      var handler: (PredictionImportResponse) => Unit = _

      override def sendRequestToExchange(msg: PredictionImportRequest) = {
        val predictionImportResponse = PredictionImportResponse(
          jobId            = msg.jobId,
          pegasusJobStatus = PegasusJobStatus.Succeed
        )
        this.handler(predictionImportResponse)
      }

      override def subscribe(handler: (PredictionImportResponse) => Unit)(implicit executionContext: ExecutionContext) = {
        this.handler = handler
        mock[SubscriptionRef]
      }
    }

    val pegasusService = system.actorOf(PegasusService.props(orionIpcProvider))
  }

  "Pegasus service" should {
    "Send a job request to pegasus via Rabbit MQ and wait a response" in new Scope {
      val jobId = "some-job-id"
      val predictionImportRequest = PredictionImportRequest(
        jobId               = jobId,
        streamId            = "some-stream-id",
        albumId             = "some-album-id",
        owner               = "some-owner",
        createdAt           = ZonedDateTime.now(ZoneId.systemDefault()),
        createdBy           = CreatedBy.Taurus,
        s3PredictionCsvPath = "s3://bucket/some/path"
      )

      val res = (pegasusService ? predictionImportRequest).mapTo[PredictionImportResponse].futureValue(timeoutConfig)
      res.jobId shouldBe jobId
      res.pegasusJobStatus shouldBe PegasusJobStatus.Succeed
    }
  }
}
