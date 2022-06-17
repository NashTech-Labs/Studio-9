package aries.rest.modules

import aries.service.heartbeat.{ HeartbeatCommandService, HeartbeatQueryService }
import aries.service.job.{ JobCommandService, JobQueryService }

trait ServicesModule {
  self: AkkaModule =>

  val jobCommand = system.actorOf(JobCommandService.props(), JobCommandService.Name)
  val jobQuery = system.actorOf(JobQueryService.props(), JobQueryService.Name)
  val heartbeatCommand = system.actorOf(HeartbeatCommandService.props(), HeartbeatCommandService.Name)
  val heartbeatQuery = system.actorOf(HeartbeatQueryService.props(), HeartbeatQueryService.Name)
}
