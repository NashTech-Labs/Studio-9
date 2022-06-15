package orion.ipc.rabbitmq.setup

import orion.ipc.rabbitmq.setup.builders.MlJobTopologyBuilder

object ClusterInitializer extends App {

  Cluster(MlJobTopologyBuilder()) initialize ()
}
