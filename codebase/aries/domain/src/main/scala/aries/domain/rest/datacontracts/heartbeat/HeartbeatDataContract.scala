package aries.domain.rest.datacontracts.heartbeat

import java.util.{ Date, UUID }
import aries.domain.rest.HttpContract

import scala.concurrent.duration.FiniteDuration

/**
 * Created by anthony.difrancesco on 8/8/17.
 */
object HeartbeatDataContract {

  case class CreateRequest(
    jobId:                  UUID,
    created:                Date,
    currentProgress:        Double,
    estimatedTimeRemaining: Option[FiniteDuration]
  ) extends HttpContract

  case class SearchRequest(
    jobId: UUID
  ) extends HttpContract

  case class Response(
    id:                     UUID,
    jobId:                  UUID,
    created:                Date,
    currentProgress:        Double,
    estimatedTimeRemaining: Option[FiniteDuration]
  ) extends HttpContract

}