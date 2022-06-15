package cortex.jobmaster.common.json4s

import java.util.concurrent.TimeUnit

import cortex.api.job.message._
import cortex.common.json4s.{ JavaEnumSerializer, Json4sSupport }
import org.json4s.ext.UUIDSerializer
import org.json4s.{ DefaultFormats, Formats, ShortTypeHints, jackson }

trait CortexJson4sSupport extends Json4sSupport {

  implicit val serialization = jackson.Serialization

  implicit val formats: Formats =
    DefaultFormats
      .withHints(ShortTypeHints(CustomTypeHints.All))
      .withTypeHintFieldName("type") ++ CustomSerializers.All

}

object CustomSerializers {
  val TimeUnitSerializer = JavaEnumSerializer[TimeUnit]

  val All = Seq(UUIDSerializer, TimeUnitSerializer)
}

object CustomTypeHints {
  val All = List(classOf[SubmitJob], CancelJob.getClass, JobMasterAppReadyForTermination.getClass,
                 classOf[Heartbeat], classOf[JobStarted], classOf[JobResultSuccess], classOf[JobResultFailure])
}
