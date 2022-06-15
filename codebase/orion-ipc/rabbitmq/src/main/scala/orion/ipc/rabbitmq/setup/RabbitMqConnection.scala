package orion.ipc.rabbitmq.setup

trait RabbitMqConnection {
  val connectionFactory = new ClusterConnectionFactory
}
