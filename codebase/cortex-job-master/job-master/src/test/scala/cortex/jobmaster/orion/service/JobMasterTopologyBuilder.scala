package cortex.jobmaster.orion.service

import com.rabbitmq.client.Channel
import orion.ipc.rabbitmq.MlJobTopology._
import orion.ipc.rabbitmq.setup.builders.Builder

trait JobMasterTopologyBuilder extends Builder {

  val name: String = "JobMasterTopologyBuilder"

  def getJobId: String

  // scalastyle:off null
  def build(channel: Channel): Unit = {

    // declare job master input queue
    val jobMasterInQueue = JobMasterInQueueTemplate.format(getJobId)
    val jobMasterInRoutingKey = JobMasterInRoutingKeyTemplate.format(getJobId)

    channel.queueDeclare(jobMasterInQueue, true, false, false, null)
    channel.queueBind(jobMasterInQueue, DataDistributorExchange, jobMasterInRoutingKey)
  }
}

object JobMasterTopologyBuilder {

  def apply(jobId: String): JobMasterTopologyBuilder = new JobMasterTopologyBuilder {
    def getJobId: String = jobId
  }
}
