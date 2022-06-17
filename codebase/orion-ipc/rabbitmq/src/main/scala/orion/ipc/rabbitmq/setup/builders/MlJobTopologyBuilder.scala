package orion.ipc.rabbitmq.setup.builders

import com.rabbitmq.client.{ BuiltinExchangeType, Channel }
import orion.ipc.rabbitmq.MlJobTopology._

trait MlJobTopologyBuilder extends Builder {

  val name: String = "MlJobTopologyBuilder"

  // scalastyle:off null
  def build(channel: Channel): Unit = {

    // Declare exchanges
    channel.exchangeDeclare(GatewayExchange, BuiltinExchangeType.FANOUT, true, false, null)
    channel.exchangeDeclare(DataDistributorExchange, BuiltinExchangeType.TOPIC, true, false, null)
    channel.exchangeDeclare(LogAggregatorExchange, BuiltinExchangeType.FANOUT, true, false, null)

    // Declare Exchange-To-Exchange bindings
    channel.exchangeBind(DataDistributorExchange, GatewayExchange, "")
    channel.exchangeBind(LogAggregatorExchange, GatewayExchange, "")

    // Declare queues and its bindings
    channel.queueDeclare(NewJobQueue, true, false, false, null)
    channel.queueBind(NewJobQueue, DataDistributorExchange, NewJobRoutingKeyTemplate.format("*"))

    channel.queueDeclare(CancelJobQueue, true, false, false, null)
    channel.queueBind(CancelJobQueue, DataDistributorExchange, CancelJobRoutingKeyTemplate.format("*"))

    channel.queueDeclare(JobMasterOutQueue, true, false, false, null)
    channel.queueBind(JobMasterOutQueue, DataDistributorExchange, JobMasterOutRoutingKeyTemplate.format("*"))

    channel.queueDeclare(JobStatusQueue, true, false, false, null)
    channel.queueBind(JobStatusQueue, DataDistributorExchange, JobStatusRoutingKeyTemplate.format("*"))

    channel.queueDeclare(CleanUpResourcesQueue, true, false, false, null)
    channel.queueBind(CleanUpResourcesQueue, DataDistributorExchange, CleanUpResourcesRoutingKeyTemplate.format("*"))

    channel.queueDeclare(PegasusInQueue, true, false, false, null)
    channel.queueBind(PegasusInQueue, DataDistributorExchange, PegasusInRoutingKey.format("*"))

    channel.queueDeclare(PegasusOutQueue, true, false, false, null)
    channel.queueBind(PegasusOutQueue, DataDistributorExchange, PegasusOutRoutingKey.format("*"))

    channel.queueDeclare(ESLogAggregatorQueue, true, false, false, null)
    channel.queueBind(ESLogAggregatorQueue, LogAggregatorExchange, "#")
  }
}

object MlJobTopologyBuilder {

  def apply(): MlJobTopologyBuilder = new MlJobTopologyBuilder {}
}
