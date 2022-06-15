package orion.rest.modules

import akka.event.LoggingAdapter

trait LoggingModule {
  implicit def logger: LoggingAdapter
}
