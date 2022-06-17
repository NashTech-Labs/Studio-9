package taurus.common.json4s

import java.util.concurrent.TimeUnit

import cortex.api.job.message.{ JobResultFailure, JobResultSuccess, SubmitJob }
import org.json4s.ext.UUIDSerializer
import org.json4s.jackson.Serialization
import org.json4s.{ CustomSerializer, DefaultFormats, Formats, ShortTypeHints, jackson }
import taurus.domain.service.job._

trait TaurusJson4sSupport extends Json4sSupport {

  implicit val serialization: Serialization.type = jackson.Serialization

  implicit val formats: Formats =
    DefaultFormats
      .withHints(ShortTypeHints(CustomTypeHints.All))
      .withTypeHintFieldName("type") ++
      CustomSerializers.All

}

object CustomSerializers {

  val TimeUnitSerializer: CustomSerializer[TimeUnit] = JavaEnumSerializer[TimeUnit]
  val JobStatusSerializer: CustomSerializer[JobStatus] = EnumSerializer[JobStatus](JobStatus)

  val All = Seq(
    UUIDSerializer,
    TimeUnitSerializer,
    JobStatusSerializer
  )
}

object CustomTypeHints {
  val All = List(classOf[SubmitJob], classOf[JobResultSuccess], classOf[JobResultFailure])
}
