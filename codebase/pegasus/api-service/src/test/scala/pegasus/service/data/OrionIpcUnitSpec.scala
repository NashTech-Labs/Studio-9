package pegasus.service.data

import java.time.{ ZoneId, ZonedDateTime }

import akka.testkit.TestActorRef
import com.amazonaws.regions.Regions
import com.spingo.op_rabbit.SubscriptionRef
import cortex.api.pegasus.{ CreatedBy, PegasusJobStatus, PredictionImportRequest, PredictionImportResponse }
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.time.{ Seconds, Span }
import pegasus.common.orionipc.OrionIpcProvider
import pegasus.common.redshift.RedshiftRepository
import pegasus.common.redshift.RedshiftRepository.AwsCredentials
import pegasus.testkit.service.ServiceBaseSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future, Promise }

class OrionIpcUnitSpec extends ServiceBaseSpec {

  //scalastyle:off
  val timeoutConfig = PatienceConfiguration.Timeout(Span(5, Seconds))
  //scalastyle:on

  val mockedS3Region = Regions.US_EAST_2
  val mockAwsCredentials = AwsCredentials("access-key-id", "secret-access-key")
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
  val predictionImportResponse = PredictionImportResponse(
    jobId            = jobId,
    pegasusJobStatus = PegasusJobStatus.Succeed
  )

  trait Scope {
    val mockRepository = mock[RedshiftRepository]
    val subscribed = Promise[Boolean]()
    val responded = Promise[Boolean]()

    val dataCommandService = TestActorRef(new DataCommandService {
      override val repository = mockRepository
      override val s3Region = mockedS3Region
      override val s3Credentials = mockAwsCredentials
    })

    val orionIpcProvider = new OrionIpcProvider(system) {
      override def respondToExchange(msg: PredictionImportResponse) = {
        responded.success(true)
      }

      override def subscribe(handler: (PredictionImportRequest) => Unit)(implicit executionContext: ExecutionContext): SubscriptionRef = {
        subscribed.success(true)
        handler(predictionImportRequest)
        mock[SubscriptionRef]
      }
    }
  }

  "When a message is pushed into Rabbit MQ" should {
    "Data command service will receive prediction upload job, run it, and respond back to orion ipc provider" +
      "who will respond to exchange" in new Scope {
        (mockRepository.uploadPrediction _)
          .expects(predictionImportRequest, mockAwsCredentials, mockedS3Region)
          .returning(Future.successful(()))
          .once()

        // when it starts orionIpcProvide.subscribe should be automatically triggered
        TestActorRef(new OrionIpcProxyService(dataCommandService, orionIpcProvider))

        subscribed.future.futureValue(timeoutConfig) shouldBe true
        responded.future.futureValue(timeoutConfig) shouldBe true
      }
  }
}
