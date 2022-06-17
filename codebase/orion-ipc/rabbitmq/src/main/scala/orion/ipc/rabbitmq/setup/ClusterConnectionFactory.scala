package orion.ipc.rabbitmq.setup

import com.rabbitmq.client.ConnectionFactory
import com.spingo.op_rabbit.ConnectionParams

import scala.collection.JavaConversions.mapAsJavaMap

class ClusterConnectionFactory extends ConnectionFactory() {
  import com.rabbitmq.client.{ Address, Connection }
  var hosts = Array.empty[Address]
  /**
   * Configures connection factory to connect to one of the following hosts.
   */
  def setHosts(newHosts: Array[Address]): Unit = { hosts = newHosts }

  override def getHost: String = {
    if (hosts.nonEmpty) {
      s"{${hosts.mkString(",")}}"
    } else {
      super.getHost
    }
  }

  override def newConnection(): Connection = {
    if (hosts.nonEmpty) this.newConnection(hosts) else super.newConnection()
  }

  def applyTo(factory: ClusterConnectionFactory)(implicit connectionParams: ConnectionParams): Unit = {
    factory.setHosts(connectionParams.hosts.toArray)
    factory.setUsername(connectionParams.username)
    factory.setPassword(connectionParams.password)
    factory.setVirtualHost(connectionParams.virtualHost)
    // Replace the table of client properties that will be sent to the server during subsequent connection startups.
    factory.setClientProperties(mapAsJavaMap(connectionParams.clientProperties))
    factory.setConnectionTimeout(connectionParams.connectionTimeout)
    factory.setExceptionHandler(connectionParams.exceptionHandler)
    factory.setRequestedChannelMax(connectionParams.requestedChannelMax)
    factory.setRequestedFrameMax(connectionParams.requestedFrameMax)
    factory.setRequestedHeartbeat(connectionParams.requestedHeartbeat)
    factory.setSaslConfig(connectionParams.saslConfig)
    connectionParams.sharedExecutor foreach factory.setSharedExecutor
    factory.setShutdownTimeout(connectionParams.shutdownTimeout)
    factory.setSocketFactory(connectionParams.socketFactory)
    if (connectionParams.ssl) factory.useSslProtocol()
  }
}
