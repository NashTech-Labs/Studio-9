package orion.ipc.rabbitmq.setup

import com.spingo.op_rabbit.ConnectionParams
import org.slf4j.LoggerFactory
import orion.ipc.common.using
import orion.ipc.rabbitmq.setup.builders.Builder

trait Cluster {
  self: RabbitMqConnection =>

  protected def builders: Seq[Builder]

  private val logger = LoggerFactory.getLogger("RabbitMQClusterInitializer")

  def initialize(): Unit = {
    connectionFactory.applyTo(connectionFactory) {
      ConnectionParams.fromConfig()
    }

    using(connectionFactory.newConnection()) {
      connection =>
        val channel = connection.createChannel()
        builders.foreach { builder =>
          logger.info(s"Starting to build ${builder.name()}")
          builder.build(channel)
          logger.info(s"${builder.name()} has been completed")
        }
        if (channel.isOpen) channel.close()
    }
  }
}

object Cluster {

  def apply(args: Builder*): Cluster = new Cluster with RabbitMqConnection {
    override protected def builders: Seq[Builder] = args
  }
}

