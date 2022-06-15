package taurus.job

import java.util.UUID

import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.{ HttpEntity, HttpResponse }
import akka.testkit.{ TestActorRef, TestProbe }
import awscala.sqs.{ Message, Queue }
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.time.{ Millis, Span }
import taurus.job.BatchJobService.{ OnlinePredictionBatchJob, OnlinePredictionBatchJobSettings }
import taurus.sqs.SQSMessagesProvider
import taurus.testkit.service.ServiceBaseSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class BatchJobServiceUnitSpec extends ServiceBaseSpec {
  import BatchJobServiceUnitSpec.Fixtures._

  //scalastyle:off
  implicit val patienceConfig = Eventually.PatienceConfig(
    timeout  = Span(3000, Millis),
    interval = Span(100, Millis)
  )
  //scalastyle:on

  trait Scope {
    val mockBatchJobHandler = TestProbe()
    val sampleMessage = Message(mock[Queue], "", sqsMessageRawBody, "", Map())
    val messages = (0 until 5).map(_ => sampleMessage)
  }

  "batch job service" must {
    "parse argo configuration" in new Scope {
      val sqsMessagesProvider = mock[SQSMessagesProvider]
      val batchJobService = TestActorRef(new BatchJobService(
        batchJobMessageHandler = mockBatchJobHandler.ref,
        sqsMessagesProvider    = sqsMessagesProvider,
        streamId               = UUID.randomUUID.toString,
        batchSize              = 1,
        queueCheckInterval     = 1.seconds,
        forceFetchTimeout      = 1.seconds
      ) {
        override def preStart() = ()
        override def get(uri: String, credentials: Option[HttpCredentials]): Future[HttpResponse] = {
          Future.successful(HttpResponse(entity = HttpEntity(argoResponseRawBody)))
        }
      })

      val configuration = batchJobService.underlyingActor.fetchOnlineJobConfiguration().futureValue
      configuration.streamId shouldBe "stream-id"
      configuration.owner shouldBe "owner"
      configuration.modelId shouldBe "model-id"
    }

    "delegate batch job to batch job handler after creating it" in new Scope {
      val sqsMessagesProvider = mock[SQSMessagesProvider]
      (sqsMessagesProvider.receiveMessages _).expects().returns(messages).once()
      (sqsMessagesProvider.deleteMessages _).expects(*).returns(*).once()

      val batchJobService = TestActorRef(new BatchJobService(
        batchJobMessageHandler = mockBatchJobHandler.ref,
        sqsMessagesProvider    = sqsMessagesProvider,
        streamId               = UUID.randomUUID.toString,
        batchSize              = 1,
        queueCheckInterval     = 1.seconds,
        forceFetchTimeout      = 1.seconds
      ) {
        override def preStart() = ()
        override def get(uri: String, credentials: Option[HttpCredentials]): Future[HttpResponse] = {
          Future.successful(HttpResponse(entity = HttpEntity(argoResponseRawBody)))
        }
      })

      batchJobService.underlyingActor.createOnlinePredictionJob()
      val msg = mockBatchJobHandler.expectMsgType[OnlinePredictionBatchJob]
      msg.records.size shouldBe messages.size
      msg.records.foreach(x => {
        x.key shouldBe "someImage.png"
        x.size shouldBe 196293
      })
    }

    "try to fetch messages even if amount of messages in queue less than batch size" in new Scope {
      val sqsMessagesProvider = mock[SQSMessagesProvider]
      (sqsMessagesProvider.receiveMessages _).expects().returns(Seq(sampleMessage)).once()
      (sqsMessagesProvider.getApproximateNumberOfMessages _).expects().returns(1).anyNumberOfTimes()
      (sqsMessagesProvider.deleteMessages _).expects(*).returns(*).once()

      val batchJobService = TestActorRef(new BatchJobService(
        batchJobMessageHandler = mockBatchJobHandler.ref,
        sqsMessagesProvider    = sqsMessagesProvider,
        batchSize              = 10,
        streamId               = UUID.randomUUID.toString,
        queueCheckInterval     = 500.millis,
        forceFetchTimeout      = 2.seconds
      ) {
        override def get(uri: String, credentials: Option[HttpCredentials]): Future[HttpResponse] = {
          Future.successful(HttpResponse(entity = HttpEntity(argoResponseRawBody)))
        }
        override def fetchOnlineJobConfiguration() = Future.successful(mock[OnlinePredictionBatchJobSettings])
        override def restartTimer() = ()
      })

      Eventually.eventually {
        mockBatchJobHandler.expectMsgType[OnlinePredictionBatchJob]
      }
    }
  }
}

object BatchJobServiceUnitSpec {
  object Fixtures {
    //scalastyle:off
    val sqsMessageRawBody =
      """{
        "Type" : "Notification",
        "MessageId" : "8d5d258a-69c6-53a4-aed4-769a3519829d",
        "TopicArn" : "arn:aws:sns:us-east-1:068078214683:online-prediction-default",
        "Subject" : "Amazon S3 Notification",
        "Message" : "{\"Records\":[{\"eventVersion\":\"2.0\",\"eventSource\":\"aws:s3\",\"awsRegion\":\"us-east-1\", \"eventTime\":\"2018-02-28T07:30:38.832Z\",\"eventName\":\"ObjectCreated:Put\",\"userIdentity\":{\"principalId\":\"AWS:AIDAITVFW7BOHKLENHDZS\"},\"requestParameters\":{\"sourceIPAddress\":\"91.221.61.126\"},\"responseElements\":{\"x-amz-request-id\":\"D35F5FB9A8020280\",\"x-amz-id-2\":\"shEP6/Kt+oE8NTJCQ8ZgUCreCp4za0eAYioBi8jHo0lKslulDF48KZSNPEYSUjdKc997YG1SsLY=\"},\"s3\":{\"s3SchemaVersion\":\"1.0\",\"configurationId\":\"ZDQ0Njc3YjAtYmU5Yy00ZGVmLWEyMWItMjdkZDIyM2E2Mzhj\",\"bucket\":{\"name\":\"online-prediction-input-default.dev.deepcortex.ai\",\"ownerIdentity\":{\"principalId\":\"AV376ZRASYGOG\"},\"arn\":\"arn:aws:s3:::online-prediction-input-default.dev.deepcortex.ai\"},\"object\":{\"key\":\"someImage.png\",\"size\":196293,\"eTag\":\"e35c6459145087519aa02c0ee2baa712\",\"sequencer\":\"005A965A9DDBE4BB7A\"}}}]}",
        "Timestamp" : "2018-02-28T07:30:39.087Z",
        "SignatureVersion" : "1",
        "Signature" : "U39asdIfKoGZr3ZLlKeTQ56SnSUIpzG37xNU3raLYZmr7hzThDLW9pact0/4ofS1rykgLUeZPj/VRfDZtWfL6pooLqF84++GJZ03l0mvORvKtHc7nCGpheq2YwCP3msd0ZpN3fxsNV6OEebIZiSh2+C5P2yr82i3OsLqk7wTmFJDs+Re8WaYgNloGZnftby82K9GpQS1f+8ezlKnQypTKAtBw0i83Ax4nx34SD77b1lWNMP+Go7zSk9sY0LlJAphqAgHX2AXMuKse7uyLbB9bEz7dEJQBNdIlL5i6nAwX34wgcRNzmddubnB+qaCf9K1B3J1DARMrqEWKND6+y6kcw==",
        "SigningCertURL" : "https://sns.us-east-1.amazonaws.com/SimpleNotificationService-433026a4050d206028891664da859041.pem",
        "UnsubscribeURL" : "https://sns.us-east-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:us-east-1:068078214683:online-prediction-default:135599b9-36f9-4168-87c0-fc301b760549"
      }"""

    val argoResponseRawBody = """{
      "serviceName": "online-prediction",
      "settingName": "stream_dd3b662c-1545-4fb9-9617-b98ab2581326",
      "settingValue": "{\"id\":\"stream-id\",\"s3Settings\":{\"awsRegion\":\"us-east-1\",\"awsAccessKey\":\"access-key\",\"awsSecretKey\":\"secret-key\",\"bucketName\":\"bucket-name\",\"imagesPath\":\"images\"},\"modelId\":\"model-id\",\"albumId\":\"albumid\",\"owner\":\"owner\",\"targetPrefix\":\"target-prefix\"}",
      "tags": [
      "jobA"
      ],
      "createdAt": "2018-02-26T15:58:14Z",
      "updatedAt": "2018-02-26T15:58:14Z"
    }"""
  }
}
