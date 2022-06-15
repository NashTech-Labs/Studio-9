package aries.common.json4s

import org.json4s.{ DefaultFormats, Formats, Serialization, jackson }

trait Json4sSupport {
  implicit val serialization: Serialization
  implicit val formats: Formats
}

trait DefaultJson4sSupport extends Json4sSupport {
  implicit val serialization = jackson.Serialization
  implicit val formats: Formats = DefaultFormats
}