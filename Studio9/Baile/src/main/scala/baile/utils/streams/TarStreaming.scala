package baile.utils.streams

import java.nio.charset.StandardCharsets
import java.util.Date

import akka.NotUsed
import akka.stream.scaladsl.{ Flow, Source }
import akka.util.ByteString
import org.apache.commons.compress.archivers.tar.{ TarArchiveEntry, TarConstants }

object TarStreaming {

  private val RecordSize = 512
  private val EOFBlockSize = RecordSize * 2

  private val TerminalChunk: ByteString = ByteString.fromArray(Array.ofDim[Byte](EOFBlockSize))

  val TarFlow: Flow[TarFile, ByteString, NotUsed] = {
    Flow[TarFile].flatMapConcat { file =>

      def buildPadding(size: Long): Array[Byte] = {
        val remainder = size % RecordSize
        if (remainder == 0) {
          Array.emptyByteArray
        } else {
          Array.ofDim[Byte](RecordSize - remainder.toInt)
        }
      }

      def buildHeaderBlock(): ByteString = {
        val buffer = Array.ofDim[Byte](RecordSize)
        val builder = ByteString.newBuilder

        val nameAsBytes = file.name.getBytes(StandardCharsets.UTF_8)

        def appendTarArchiveEntry(header: TarArchiveEntry): Unit = {
          header.writeEntryHeader(buffer)
          builder ++= buffer
        }

        if (nameAsBytes.length > TarConstants.NAMELEN) {
          val longNameArchiveEntry = new TarArchiveEntry(TarConstants.GNU_LONGLINK, TarConstants.LF_GNUTYPE_LONGNAME)
          longNameArchiveEntry.setSize(nameAsBytes.length.toLong + 1L) // +1 for null byte
          appendTarArchiveEntry(longNameArchiveEntry)
          builder ++= nameAsBytes
          builder += 0
          val padding = buildPadding(builder.length)
          builder ++= padding
        }

        val simpleTarArchiveEntry = new TarArchiveEntry(file.name)
        simpleTarArchiveEntry.setSize(file.size)
        simpleTarArchiveEntry.setModTime(file.modificationTime)
        appendTarArchiveEntry(simpleTarArchiveEntry)

        builder.result()
      }

      def buildContentBlockSource(): Source[ByteString, NotUsed] = {
        val padding = buildPadding(file.size)
        file.content ++ Source.single(ByteString(padding))
      }

      Source.single(buildHeaderBlock()) ++ buildContentBlockSource()

    } ++ Source.single(TerminalChunk)
  }

  case class TarFile(name: String, size: Long, modificationTime: Date, content: Source[ByteString, NotUsed])

}
