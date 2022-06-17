package sqlserver.services.info

import java.lang.management.ManagementFactory

import akka.event.LoggingAdapter
import sqlserver.domain.info.Status

import scala.concurrent.duration.{ Duration, MILLISECONDS }
import scala.concurrent.{ ExecutionContext, Future }

class InfoService(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) {

  def getUptime(): Future[Status] = Future {
    Status(Duration(ManagementFactory.getRuntimeMXBean.getUptime, MILLISECONDS))
  }

}
