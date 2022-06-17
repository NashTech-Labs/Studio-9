package gemini.services.jupyter

import akka.cluster.sharding.ShardRegion

class JupyterSessionSupervisorSharding(numberOfShards: Int) {

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: JupyterSessionSupervisor.Message => (msg.sessionId.toString, msg)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case msg: JupyterSessionSupervisor.Message => (math.abs(msg.sessionId.toString.hashCode) % numberOfShards).toString
  }

}
