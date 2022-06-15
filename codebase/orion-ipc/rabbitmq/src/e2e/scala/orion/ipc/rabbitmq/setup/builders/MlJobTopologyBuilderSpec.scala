package orion.ipc.rabbitmq.setup.builders

import orion.ipc.rabbitmq.DockerRabbitMqServiceTestSpec
import orion.ipc.rabbitmq.MlJobTopology._

class MlJobTopologyBuilderSpec extends MlJobTopologyBuilder with DockerRabbitMqServiceTestSpec {

  val testMessage = "test message"

  "MlJobTopologyBuilder" should {

    "have a name" in {
      name shouldBe "MlJobTopologyBuilder"
    }

    "be created by a factory" in {
      MlJobTopologyBuilder().name shouldBe "MlJobTopologyBuilder"
    }

    "created exchanges" in {
      noException should be thrownBy channel.exchangeDeclarePassive(GatewayExchange)
      noException should be thrownBy channel.exchangeDeclarePassive(DataDistributorExchange)
      noException should be thrownBy channel.exchangeDeclarePassive(LogAggregatorExchange)
    }

    "created queues" in {
      noException should be thrownBy channel.queueDeclarePassive(NewJobQueue)
      noException should be thrownBy channel.queueDeclarePassive(CancelJobQueue)
      noException should be thrownBy channel.queueDeclarePassive(JobMasterOutQueue)
      noException should be thrownBy channel.queueDeclarePassive(JobStatusQueue)
      noException should be thrownBy channel.queueDeclarePassive(PegasusInQueue)
      noException should be thrownBy channel.queueDeclarePassive(PegasusOutQueue)
      noException should be thrownBy channel.queueDeclarePassive(ESLogAggregatorQueue)
    }

    "setup New Job workflow" in {
      sendMessage(GatewayExchange, newJobRoutingKey, testMessage)
      val (routingKey, message) = getMessage(NewJobQueue).futureValue
      routingKey shouldBe newJobRoutingKey
      message shouldBe testMessage

      getMessage(ESLogAggregatorQueue).futureValue should be ((newJobRoutingKey, testMessage))
    }

    "setup Cancel Job workflow" in {
      sendMessage(GatewayExchange, cancelJobRoutingKey, testMessage)
      val (routingKey, message) = getMessage(CancelJobQueue).futureValue
      routingKey shouldBe cancelJobRoutingKey
      message shouldBe testMessage

      getMessage(ESLogAggregatorQueue).futureValue should be ((cancelJobRoutingKey, testMessage))
    }

    "setup Log Aggregator workflow" in {
      sendMessage(GatewayExchange, notExistsRoutingKey, testMessage)
      val (routingKey, message) = getMessage(ESLogAggregatorQueue).futureValue
      routingKey shouldBe notExistsRoutingKey
      message shouldBe testMessage
    }

    // scalastyle:off null
    "setup JobMaster.IN workflow" in {
      //in queue
      channel.queueDeclare(jobMasterInQueue, true, false, false, null)

      channel.queueBind(jobMasterInQueue, DataDistributorExchange, jobMasterInRoutingKey)

      sendMessage(GatewayExchange, jobMasterInRoutingKey, testMessage)
      val (routingKey, message) = getMessage(jobMasterInQueue).futureValue
      routingKey shouldBe jobMasterInRoutingKey
      message shouldBe testMessage

      getMessage(ESLogAggregatorQueue).futureValue should be ((jobMasterInRoutingKey, testMessage))
      channel.queueDelete(jobMasterInQueue)
    }

    "setup JobMaster.OUT workflow" in {
      sendMessage(GatewayExchange, jobMasterOutRoutingKey, testMessage)
      val (routingKey, message) = getMessage(JobMasterOutQueue).futureValue
      routingKey shouldBe jobMasterOutRoutingKey
      message shouldBe testMessage

      getMessage(ESLogAggregatorQueue).futureValue should be ((jobMasterOutRoutingKey, testMessage))
    }

    "setup JobStatus workflow" in {
      sendMessage(GatewayExchange, jobStatusRoutingKey, testMessage)
      val (routingKey, message) = getMessage(JobStatusQueue).futureValue
      routingKey shouldBe jobStatusRoutingKey
      message shouldBe testMessage

      getMessage(ESLogAggregatorQueue).futureValue should be ((jobStatusRoutingKey, testMessage))
    }

    "setup Cleanup Resources workflow" in {
      sendMessage(GatewayExchange, cleanUpResourcesRoutingKey, testMessage)
      val (routingKey, message) = getMessage(CleanUpResourcesQueue).futureValue
      routingKey shouldBe cleanUpResourcesRoutingKey
      message shouldBe testMessage

      getMessage(ESLogAggregatorQueue).futureValue should be ((cleanUpResourcesRoutingKey, testMessage))
    }

    "setup Pegasus.IN workflow" in {
      sendMessage(GatewayExchange, pegasusInRoutingKey, testMessage)
      val (routingKey, message) = getMessage(PegasusInQueue).futureValue
      routingKey shouldBe pegasusInRoutingKey
      message shouldBe testMessage

      getMessage(ESLogAggregatorQueue).futureValue should be ((PegasusInRoutingKey, testMessage))
    }

    "setup Pegasus.OUT workflow" in {
      sendMessage(GatewayExchange, pegasusOutRoutingKey, testMessage)
      val (routingKey, message) = getMessage(PegasusOutQueue).futureValue
      routingKey shouldBe pegasusOutRoutingKey
      message shouldBe testMessage

      getMessage(ESLogAggregatorQueue).futureValue should be ((PegasusOutRoutingKey, testMessage))
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    build(channel)
  }
}
