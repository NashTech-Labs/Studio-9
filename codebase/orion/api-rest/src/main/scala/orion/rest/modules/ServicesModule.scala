package orion.rest.modules

import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings }
import akka.routing.FromConfig
import orion.service.job.{ JobDispatcher, JobResourcesCleaner, JobSupervisor }

trait ServicesModule { self: AkkaModule =>

  // Initiates JobSupervisor ShardRegion
  val jobSupervisorShardRegion =
    ClusterSharding(system).start(
      typeName        = JobSupervisor.Name,
      entityProps     = JobSupervisor.props(),
      settings        = ClusterShardingSettings(system),
      extractEntityId = JobSupervisor.Sharding.extractEntityId,
      extractShardId  = JobSupervisor.Sharding.extractShardId
    )

  // Initiates JobDispatcher local router
  system.actorOf(FromConfig.props(JobDispatcher.props(jobSupervisorShardRegion)), JobDispatcher.Name)

  // Initiates JobResourcesCleaner local router
  system.actorOf(FromConfig.props(JobResourcesCleaner.props), JobResourcesCleaner.Name)
}
