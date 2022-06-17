package cortex.jobmaster.orion.service.io

import com.trueaccord.scalapb.{ GeneratedMessage, GeneratedMessageCompanion, Message }
import play.api.libs.json.{ Reads, Writes }

trait BaseStorageFactory {

  def createParamResultStorageReader[T <: GeneratedMessage with Message[T]: GeneratedMessageCompanion](): StorageReader[T]

  def createParamResultStorageWriter[T <: GeneratedMessage](): StorageWriter[T]

  def createStorageReader[T](unmarshaller: Option[Unmarshaller[T, Array[Byte]]] = None)(implicit reads: Reads[T]): StorageReader[T]

  def createStorageWriter[T](marshaller: Option[Marshaller[T, Array[Byte]]] = None)(implicit writes: Writes[T]): StorageWriter[T]
}
