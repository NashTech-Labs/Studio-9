package baile.services.remotestorage

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString

case class StreamedFile(file: File, content: Source[ByteString, NotUsed])
