package taurus.sqs

import taurus.testkit.E2ESpec
import awscala._
import sqs._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class SQSMessagesProviderUnitSpec extends E2ESpec {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(
      timeout = 1 minute,
      interval = 1 second
    )

  implicit val sqs = SQS.at(Region.US_EAST_1)
  val numberOfMessages = 100
  val batchSize = 10

  "SQS raw client" should {
    "receive all messages from a queue" in {
      val queueName = "online-prediction-default-sqs-client-e2e-test"

      whenReady(createQueue(queueName)) { queue =>
        sendMessages(queue)

        val approximateNumberOfMessages = getApproximateNumberOfMessages(queue)

        val result: Seq[Message] =
          for {
            _ <- 0 until approximateNumberOfMessages / batchSize
            message <- sqs.receiveMessage(queue, batchSize)
          } yield message
        result.size should (be >= batchSize and be <= numberOfMessages)

        result.sliding(batchSize).foreach(batch => queue.removeAll(batch))
        val approximateNumberOfMessagesAfterRemoval = getApproximateNumberOfMessages(queue)
        approximateNumberOfMessagesAfterRemoval shouldBe numberOfMessages - result.size

        queue.destroy()
      }
    }
  }

  "SQS messages provider" should {

    "receive all messages from a queue" in {
      val queueName = "online-prediction-default-e2e-test"

      whenReady(createQueue(queueName)) { queue =>
        sendMessages(queue)

        val sqsMessagesProvider = SQSMessagesProvider(queueName, batchSize)
        val messages = sqsMessagesProvider.receiveMessages()

        messages.size should (be >= batchSize and be <= numberOfMessages)

        sqsMessagesProvider.deleteMessages(messages)
        val approximateNumberOfMessagesAfterRemoval = getApproximateNumberOfMessages(queue)
        approximateNumberOfMessagesAfterRemoval shouldBe numberOfMessages - messages.size

        queue.destroy()
      }
    }

    "throw exception when queue does not exist" in {
      val sqsMessagesProvider = SQSMessagesProvider("foo", batchSize)

      intercept[Exception] {
        sqsMessagesProvider.receiveMessages()
      }
    }

  }

  private def createQueue(name: String)(implicit sqs: SQS): Future[Queue] = {

    def poll(): Future[Queue] = {
      sqs.queue(name) match {
        case Some(queue) => Future.successful(queue)
        case None => akka.pattern.after(1 second, system.scheduler)(poll())(system.dispatcher)
      }
    }

    sqs.createQueue(name)
    poll()
  }

  private def sendMessages(queue: Queue)(implicit sqs: SQS): Unit =
    for (batchNumber <- 0 until numberOfMessages / batchSize) {
      val messages = (1 to batchSize).map(message => (message + batchNumber * batchSize).toString)
      queue.addAll(messages)
    }

  private def getApproximateNumberOfMessages(queue: Queue)(implicit sqs: SQS): Int = {
    val attributeName = "ApproximateNumberOfMessages"
    val numberStr = sqs.queueAttributes(queue, attributeName).getOrElse(attributeName, "0")
    Try(numberStr.trim.toInt).getOrElse(0)
  }


}
