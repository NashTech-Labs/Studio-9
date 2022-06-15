package cortex.jobmaster.orion.ipc

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.util.Timeout
import com.spingo.op_rabbit.{ Message, RecoveryStrategy }
import com.spingo.op_rabbit.Message.Ack
import com.whisk.docker.scalatest.DockerTestKit
import cortex.testkit.{ BaseSpec, DockerRabbitMqService }
import org.json4s.DefaultFormats
import orion.ipc.rabbitmq.MlJobTopology.GatewayExchange
import orion.ipc.rabbitmq.setup.Cluster
import orion.ipc.rabbitmq.setup.builders.MlJobTopologyBuilder
import orion.ipc.rabbitmq.{ MlJobTopology, RabbitMqRpcClient }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future, Promise }
import scala.language.postfixOps

class RabbitMqRpcClientSpec extends BaseSpec with DockerRabbitMqService with DockerTestKit {

  implicit val formats = DefaultFormats
  implicit val timeout = Timeout(30 seconds)
  implicit val recoveryStrategy = RecoveryStrategy.limitedRedeliver()

  val msg = Data("1")
  val newJobRoutingKey: String = MlJobTopology.NewJobRoutingKeyTemplate.format("test-job-id")

  "RabbitMqRpcClient" should {

    "send a message to exchange" in {

      val actorSystem: ActorSystem = ActorSystem("RabbitMqRpcClientSpec")

      val rabbitMqRpcClient = RabbitMqRpcClient(actorSystem)

      rabbitMqRpcClient.sendMessageToExchange[Data](msg, MlJobTopology.GatewayExchange, newJobRoutingKey)

      actorSystem.terminate()
      Await.ready(actorSystem.whenTerminated, Duration(1, TimeUnit.MINUTES))
    }

    "send a message with confirmation to exchange" in {

      val actorSystem: ActorSystem = ActorSystem("RabbitMqRpcClientSpec")

      val rabbitMqRpcClient = RabbitMqRpcClient(actorSystem)

      val confirmation = rabbitMqRpcClient.sendMessageToExchangeWithConfirmation[Data](msg, GatewayExchange, newJobRoutingKey)
      confirmation.futureValue should matchPattern { case Message.Ack(_) => }

      actorSystem.terminate()
      Await.ready(actorSystem.whenTerminated, Duration(1, TimeUnit.MINUTES))
    }

    "subscribe to a queue" in {
      val numberOfMessages = 10
      @volatile var msgCount: Int = numberOfMessages

      val actorSystem: ActorSystem = ActorSystem("RabbitMqRpcClientSpec")

      val rabbitMqRpcClient = RabbitMqRpcClient(actorSystem)

      (1 to numberOfMessages) foreach { n =>
        rabbitMqRpcClient.sendMessageToExchange[Data](Data(n.toString), MlJobTopology.GatewayExchange, newJobRoutingKey)
      }

      val p = Promise[Boolean]()

      val subscriber = rabbitMqRpcClient.subscribe[Data](MlJobTopology.NewJobQueue) {
        _: Data =>
          msgCount -= 1
          if (msgCount == 0) p.success(true)
      }

      p.future.map { result =>
        result shouldBe true
        subscriber.close()
      }

      actorSystem.terminate()
      Await.ready(actorSystem.whenTerminated, Duration(1, TimeUnit.MINUTES))
    }

    "provide the full example of usage" in {

      val actorSystem: ActorSystem = ActorSystem("RabbitMqRpcClientSpec")

      val rabbitMqRpcClient = RabbitMqRpcClient(actorSystem)

      val confirmation: Future[Ack] = rabbitMqRpcClient.sendMessageToExchangeWithConfirmation[Data](msg, MlJobTopology.GatewayExchange, newJobRoutingKey)
      // You can check that confirmation Future is successful

      val subscriber = rabbitMqRpcClient.subscribe[Data](MlJobTopology.NewJobQueue) {
        msg: Data => //put your code here
      }

      subscriber.close()

      actorSystem.terminate()
      Await.ready(actorSystem.whenTerminated, Duration(1, TimeUnit.MINUTES))
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    Cluster(MlJobTopologyBuilder()) initialize ()
  }
}



