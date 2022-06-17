package taurus.rest.modules

import taurus.OnlinePredictionDispatcher
import taurus.pegasus.{ OrionIpcProvider, PegasusService }

trait ServicesModule { self: AkkaModule with SettingsModule =>

  // TODO: consider creating BatchProcessor as a Singleton actor taking into consideration we're going to have
  // multiple Taurus instances running in Mesos. If that's the case, BatchProcessorWorkers should be created as
  // a Cluster Router and the workers should not stop themselves once the message processing is done.

  val pegasusService = system.actorOf(PegasusService.props(OrionIpcProvider(system)))

  val onlinePredictionDispatcher = system.actorOf(OnlinePredictionDispatcher.props(config.streamId, pegasusService))
}
