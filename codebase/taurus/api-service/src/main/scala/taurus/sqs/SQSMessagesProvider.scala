package taurus.sqs

import awscala.sqs.{ Message, Queue, SQS }
import org.json4s.DefaultReaders._
import org.json4s.jackson.JsonMethods.parse

import scala.util.Try

class SQSMessagesProvider(val queueName: String, val batchSize: Int)(implicit val sqs: SQS) {

  protected def getQueue(): Queue = {
    sqs.queue(queueName).getOrElse(
      throw new Exception(s"Queue $queueName does not exist")
    )
  }

  def getApproximateNumberOfMessages(): Int = {
    val queue = getQueue()
    val attributeName = "ApproximateNumberOfMessages"
    val numberStr = sqs.queueAttributes(queue, attributeName).getOrElse(attributeName, "0")
    Try(numberStr.trim.toInt).getOrElse(0)
  }

  def receiveMessages(): Seq[Message] = {
    val approximateNumberOfMessages = getApproximateNumberOfMessages()
    val steps = if (approximateNumberOfMessages < batchSize) approximateNumberOfMessages else approximateNumberOfMessages / batchSize

    for {
      _ <- 0 until steps
      queue = getQueue()
      message <- sqs.receiveMessage(queue, batchSize)
    } yield message
  }

  def deleteMessages(messages: Seq[Message]): Unit = {
    val queue = getQueue()

    messages.sliding(batchSize).foreach(batch => queue.removeAll(batch))
  }

}

object SQSMessagesProvider {

  //scalastyle:off
  def apply(queue: String, batchSize: Int = 10)(implicit sqs: SQS): SQSMessagesProvider = {
    new SQSMessagesProvider(
      queueName = queue,
      batchSize = batchSize
    )
  }
  //scalastyle:on

  case class S3Record(key: String, size: Long)

  implicit class OnlinePredictionRecordConverter(msg: Message) {
    def toS3Record: S3Record = {
      val json = parse(msg.body)
      val message = parse((json \\ "Message").as[String])
      val key = (message \\ "key").as[String]
      val size = (message \\ "size").as[Long]
      S3Record(key, size)
    }
  }
}
