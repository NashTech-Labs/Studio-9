package pegasus.common.json4s

import org.json4s.ext.UUIDSerializer
import org.json4s.{ DefaultFormats, Formats, ShortTypeHints, jackson }

trait PegasusJson4sSupport extends Json4sSupport {

  implicit val serialization = jackson.Serialization

  implicit val formats: Formats =
    DefaultFormats
      .withHints(ShortTypeHints(CustomTypeHints.All))
      .withTypeHintFieldName("type") ++ CustomSerializers.All

}

object CustomSerializers {
  val All = Seq(UUIDSerializer)
}

object CustomTypeHints {
  val All = List()
}