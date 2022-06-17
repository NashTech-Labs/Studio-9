package pegasus.rest.modules

import pegasus.common.orionipc.OrionIpcProvider
import pegasus.service.data.{ DataCommandService, OrionIpcProxyService }

trait ServicesModule {
  self: AkkaModule =>

  val dataCommandService = system.actorOf(DataCommandService.props(), DataCommandService.Name)
  val orionIpcProvider = OrionIpcProvider(system)
  val props = OrionIpcProxyService.props(dataCommandService, orionIpcProvider)
  val orionIpcProxy = system.actorOf(props, OrionIpcProxyService.Name)
}
