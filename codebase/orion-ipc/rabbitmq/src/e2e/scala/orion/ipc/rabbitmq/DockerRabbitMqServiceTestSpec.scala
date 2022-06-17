package orion.ipc.rabbitmq

import com.rabbitmq.client._
import com.whisk.docker.scalatest.DockerTestKit
import org.scalatest.time.{ Millis, Span }
import orion.ipc.testkit.BaseSpec
import orion.ipc.rabbitmq.MlJobTopology._

import scala.concurrent.{ Future, Promise }

trait DockerRabbitMqServiceTestSpec extends BaseSpec with DockerRabbitMqService with DockerTestKit {
  var connection: Connection = _
  var channel: Channel = _

  val patienceTimeout = 10000L
  val patienceInterval = 500L
  implicit val defaultPatience = PatienceConfig(timeout = Span(patienceTimeout, Millis), interval = Span(patienceInterval, Millis))

  val newJobRoutingKey: String = NewJobRoutingKeyTemplate.format("test-job-id")
  val cancelJobRoutingKey: String = CancelJobRoutingKeyTemplate.format("test-job-id")
  val cleanUpResourcesRoutingKey: String = CleanUpResourcesRoutingKeyTemplate.format("test-job-id")
  val jobMasterInRoutingKey: String = JobMasterInRoutingKeyTemplate.format("test-job-id")
  val jobMasterOutRoutingKey: String = JobMasterOutRoutingKeyTemplate.format("test-job-id")
  val jobStatusRoutingKey: String = JobStatusRoutingKeyTemplate.format("test-job-id")
  val notExistsRoutingKey: String = "not-exists-route"
  val jobMasterInQueue: String = JobMasterInQueueTemplate.format("test-job-id")
  val pegasusInRoutingKey: String = PegasusInRoutingKey.format("test-job-id")
  val pegasusOutRoutingKey: String = PegasusOutRoutingKey.format("test-job-id")

  // scalastyle:off null
  def sendMessage(exchange: String, routingKey: String, message: String): Unit = {
    channel.basicPublish(exchange, routingKey, null, message.getBytes("UTF-8"))
  }

  def getMessage(queueName: String): Future[(String, String)] = {
    val p = Promise[(String, String)]()

    val consumer: DefaultConsumer = new DefaultConsumer(channel) {

      override def handleDelivery(consumerTag: String,
                                  envelope: Envelope,
                                  properties: AMQP.BasicProperties,
                                  body: Array[Byte]): Unit = {
        val message = new String(body, "UTF-8")
        channel.basicCancel(consumerTag)
        p.success((envelope.getRoutingKey, message))
      }
    }
    channel.basicConsume(queueName, true, consumer)

    p.future
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    val factory = new ConnectionFactory()
    factory.setHost("localhost")

    connection = factory.newConnection()
    channel = connection.createChannel()
  }

  override def afterAll(): Unit = {
    if (channel.isOpen) channel.close()
    Option(connection) foreach {
      _.close()
    }
    super.afterAll()
  }
}
