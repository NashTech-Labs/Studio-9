package orion.ipc.rabbitmq

import java.util

import com.rabbitmq.client.{ BuiltinExchangeType, ConnectionFactory }
import orion.ipc.common._

class DockerRabbitMqServiceSpec extends DockerRabbitMqServiceTestSpec {

  "DockerRabbitMqService" should {

    "run RabbitMQ docker container" in {
      isContainerReady(container).futureValue shouldBe true
    }

    "provide ability to create RabbitMQ exchange" in {
      val exchangeName = "test-exchange"

      val factory = new ConnectionFactory()
      factory.setHost("localhost")

      using(factory.newConnection()) {
        connection =>
          val channel = connection.createChannel()
          channel.exchangeDeclare(exchangeName, BuiltinExchangeType.FANOUT, true, false, new util.HashMap[String, AnyRef]())
          an[java.io.IOException] should be thrownBy channel.exchangeDeclarePassive("non-exists")
          if (channel.isOpen) channel.close()
      }
    }
  }
}

