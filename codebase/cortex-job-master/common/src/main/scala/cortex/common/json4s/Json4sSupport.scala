package cortex.common.json4s

import org.json4s.{ Formats, Serialization }

trait Json4sSupport {
  implicit val serialization: Serialization
  implicit val formats: Formats
}
