package aries.common.json4s

import java.util.concurrent.TimeUnit

import aries.domain.service.job.JobStatus
import org.json4s.ext.UUIDSerializer
import org.json4s.{ DefaultFormats, Formats, ShortTypeHints, jackson }

trait AriesJson4sSupport extends Json4sSupport {

  implicit val serialization = jackson.Serialization

  implicit val formats: Formats =
    DefaultFormats
      .withHints(ShortTypeHints(CustomTypeHints.All))
      .withTypeHintFieldName("_t") ++ CustomSerializers.All

}

object CustomSerializers {

  val TimeUnitSerializer = JavaEnumSerializer[TimeUnit]
  val jobStatusSerializer = EnumSerializer[JobStatus](JobStatus)

  val All = Seq(UUIDSerializer, TimeUnitSerializer, jobStatusSerializer)
}

object CustomTypeHints {
  val All = List()
}
