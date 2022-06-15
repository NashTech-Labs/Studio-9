package cortex.api.job

import play.api.libs.json.Reads.LongReads
import play.api.libs.json.Writes.LongWrites
import play.api.libs.json._
import scala.concurrent.duration._

import scala.concurrent.duration.FiniteDuration

package object message {

  implicit val durationFormat: Format[FiniteDuration] = new Format[FiniteDuration] {

    override def writes(duration: FiniteDuration): JsValue = LongWrites.writes(duration.toNanos)

    override def reads(json: JsValue): JsResult[FiniteDuration] = LongReads.reads(json).map(_.nanos)

  }

}
