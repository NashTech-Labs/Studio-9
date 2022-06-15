package orion.ipc.rabbitmq

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.testkit.TestKit
import akka.util.Timeout
import com.spingo.op_rabbit.{Message, RabbitControl, RecoveryStrategy}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization.write
import orion.ipc.common._
import orion.ipc.rabbitmq.MlJobTopology.{DataDistributorExchange, ESLogAggregatorQueue, GatewayExchange, NewJobQueue}
import orion.ipc.rabbitmq.setup.builders.MlJobTopologyBuilder

import scala.concurrent.Promise
import scala.concurrent.duration._

class RabbitMqRpcClientSpec extends TestKit(ActorSystem("RabbitMqRpcClientSpec")) with MlJobTopologyBuilder with DockerRabbitMqServiceTestSpec {

  // Fixtures
  val msg = Data("1")

  trait Scope extends  {
    implicit val formats = DefaultFormats
    implicit val timeout = Timeout(30 seconds)

    val rabbitMqRpcClient = new RabbitMqRpcClient {
      override val rabbitControl = system.actorOf(Props(new RabbitControl))
      override val defaultRecoveryStrategy = RecoveryStrategy.limitedRedeliver()
      override val logger = Logging(system, getClass)
    }

    implicit val recoveryStrategy = rabbitMqRpcClient.defaultRecoveryStrategy
  }

  "RabbitMqRpcClient" should {

    "send a message to exchange" in new Scope {
      rabbitMqRpcClient.sendMessageToExchange[Data](msg, GatewayExchange, newJobRoutingKey)
      getMessage(NewJobQueue).futureValue should be((newJobRoutingKey, write(msg)))
      getMessage(ESLogAggregatorQueue).futureValue should be((newJobRoutingKey, write(msg)))
    }

    "send a message with confirmation to exchange" in new Scope {
      val confirmation = rabbitMqRpcClient.sendMessageToExchangeWithConfirmation[Data](msg, GatewayExchange, newJobRoutingKey)
      confirmation.futureValue should matchPattern { case Message.Ack(_) => }

      getMessage(NewJobQueue).futureValue should be((newJobRoutingKey, write(msg)))
      getMessage(ESLogAggregatorQueue).futureValue should be((newJobRoutingKey, write(msg)))
    }

    "subscribe to a queue" in new Scope {
      val numberOfMessages = 10
      @volatile var msgCount: Int = numberOfMessages

      (1 to numberOfMessages) foreach { _ =>
        rabbitMqRpcClient.sendMessageToExchange[Data](msg, GatewayExchange, newJobRoutingKey)
        getMessage(ESLogAggregatorQueue).futureValue should be((newJobRoutingKey, write(msg)))
      }

      val p = Promise[Boolean]()

      val subscriber = rabbitMqRpcClient.subscribe[Data](NewJobQueue) {
        _: Data =>
          msgCount -= 1
          if (msgCount == 0) p.success(true)
      }

      whenReady(p.future) { result =>
        result shouldBe true
      }

      subscriber.close()
    }

    "delete queue" in new Scope {
      val queueCreated = withRetry() {
        rabbitMqRpcClient.declareDirectBinding(DataDistributorExchange, jobMasterInRoutingKey, jobMasterInQueue)
      }

      whenReady(queueCreated) { _ =>
        noException should be thrownBy channel.queueDeclarePassive(jobMasterInQueue)
      }

      val queueDeleted = withRetry() {
        rabbitMqRpcClient.deleteQueue(jobMasterInQueue)
      }

      whenReady(queueDeleted) { _ =>
        an[java.io.IOException] should be thrownBy {
          //Has to be the new channel
          connection.createChannel().queueDeclarePassive(jobMasterInQueue)
        }
      }
    }

    "create the new queue and send a message to the new queue through the exchange" in new Scope {
      val queueCreated = withRetry() {
        rabbitMqRpcClient.declareDirectBinding(DataDistributorExchange, jobMasterInRoutingKey, jobMasterInQueue)
      }

      whenReady(queueCreated) { _ =>
        noException should be thrownBy channel.queueDeclarePassive(jobMasterInQueue)
        rabbitMqRpcClient.sendMessageToExchange[Data](msg, GatewayExchange, jobMasterInRoutingKey)
      }

      getMessage(jobMasterInQueue).futureValue should be((jobMasterInRoutingKey, write(msg)))
      getMessage(ESLogAggregatorQueue).futureValue should be((jobMasterInRoutingKey, write(msg)))
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    build(channel)
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
}


