package orion.ipc.rabbitmq

object MlJobTopology {

  val GatewayExchange = "ml_job.gateway"
  val DataDistributorExchange = "ml_job.data_distributor"
  val LogAggregatorExchange = "log_aggregator"

  val NewJobQueue = "ml_job.new_job"
  val CancelJobQueue = "ml_job.cancel_job"
  val JobMasterInQueueTemplate = "ml_job.job_master.in.%s"
  val JobMasterOutQueue = "ml_job.job_master.out"
  val ESLogAggregatorQueue = "log_aggregator.es"
  val JobStatusQueue = "ml_job.status"
  val CleanUpResourcesQueue = "ml_job.cleanup_resources"
  val PegasusInQueue = "ml_job.pegasus.in"
  val PegasusOutQueue = "ml_job.pegasus.out"

  val NewJobRoutingKeyTemplate = "ml_job.new_job.%s"
  val CancelJobRoutingKeyTemplate = "ml_job.cancel_job.%s"
  val JobMasterInRoutingKeyTemplate = "ml_job.job_master.in.%s"
  val JobMasterOutRoutingKeyTemplate = "ml_job.job_master.out.%s"
  val JobStatusRoutingKeyTemplate = "ml_job.status.%s"
  val CleanUpResourcesRoutingKeyTemplate = "ml_job.cleanup_resources.%s"
  val PegasusInRoutingKey = "ml_job.pegasus.in.%s"
  val PegasusOutRoutingKey = "ml_job.pegasus.out.%s"
}
