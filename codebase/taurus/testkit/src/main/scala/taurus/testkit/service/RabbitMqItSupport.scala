package taurus.testkit.service

import akka.testkit.{ DefaultTimeout, TestKitBase }
import com.rabbitmq.client._
import com.whisk.docker.scalatest.DockerTestKit
import taurus.testkit.AkkaPatienceConfiguration
import org.scalatest.{ BeforeAndAfterAll, Suite }
import orion.ipc.rabbitmq.setup.builders.MlJobTopologyBuilder
import taurus.testkit.{ AkkaPatienceConfiguration, RabbitMqClientSupport }

trait RabbitMqItSupport extends RabbitMqClientSupport
    with DockerRabbitMqService
    with DockerTestKit
    with AkkaPatienceConfiguration
    with BeforeAndAfterAll { self: TestKitBase with DefaultTimeout with Suite =>

  def buildTopology(): Unit = {
    val factory = new ConnectionFactory()
    factory.setHost("localhost")
    val connection = factory.newConnection()
    val channel = connection.createChannel()

    MlJobTopologyBuilder().build(channel)

    if (channel.isOpen) channel.close()
    connection.close()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    buildTopology()
  }

}