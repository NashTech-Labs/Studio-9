package cortex.rest.modules

import cortex.service.job.{ JobCommandService, JobQueryService }

trait ServicesModule {
  self: AkkaModule with SettingsModule =>

  val jobCommand = system.actorOf(JobCommandService.props(), JobCommandService.Name)
  val jobQuery = system.actorOf(JobQueryService.props(), JobQueryService.Name)
}
