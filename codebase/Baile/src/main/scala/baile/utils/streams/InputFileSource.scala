package baile.utils.streams

import akka.stream.scaladsl.Source
import akka.util.ByteString

case class InputFileSource(
  fileName: String,
  source: Source[ByteString, Any]
)
