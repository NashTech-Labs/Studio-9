package cortex.jobmaster.orion.service.io

import com.trueaccord.scalapb.{ GeneratedMessage, GeneratedMessageCompanion, Message }
import cortex.common.logging.JMLoggerFactory
import cortex.io.S3Client
import play.api.libs.json.{ Reads, Writes }

//TODO this class should be reorganized to a better structure
class S3ParamResultStorageFactory(
    client:         S3Client,
    val baseBucket: String,
    val basePath:   String
)(implicit val loggerFactory: JMLoggerFactory) extends BaseStorageFactory { self =>

  override def createParamResultStorageWriter[T <: GeneratedMessage](): StorageWriter[T] = {
    new S3StorageWriter[T](client, baseBucket, new PbByteArrayMarshaller[T]) {

      override protected def resolvePath(path: String): String = {
        s"$basePath/$path/results"
      }
    }
  }

  override def createParamResultStorageReader[T <: GeneratedMessage with Message[T]: GeneratedMessageCompanion](): StorageReader[T] = {
    new S3StorageReader[T](client, baseBucket, new PbByteArrayUnmarshaller[T]) {

      override protected def resolvePath(path: String): String = {
        path
      }
    }
  }

  def createStorageWriter[T](marshaller: Option[Marshaller[T, Array[Byte]]] = None)(implicit writes: Writes[T]): StorageWriter[T] = {
    val resolvedMarshaller = marshaller.getOrElse(new JsonMarshaller[T]())
    new S3StorageWriter[T](client, baseBucket, resolvedMarshaller) {

      // should return the whole path
      override protected def resolvePath(path: String): String = {
        s"$basePath/$path"
      }
    }
  }

  override def createStorageReader[T](unmarshaller: Option[Unmarshaller[T, Array[Byte]]] = None)(implicit reads: Reads[T]): StorageReader[T] = {
    val resolvedUnmarshaller = unmarshaller.getOrElse(new JsonUnmarshaller[T]())
    new S3StorageReader[T](client, baseBucket, resolvedUnmarshaller) {

      // should read from the base bucket
      override protected def resolvePath(path: String): String = {
        s"$basePath/$path"
      }
    }
  }
}
