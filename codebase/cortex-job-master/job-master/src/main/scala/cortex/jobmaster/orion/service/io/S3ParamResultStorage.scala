package cortex.jobmaster.orion.service.io

import cortex.common.Logging
import cortex.common.logging.JMLoggerFactory
import cortex.io.S3Client

abstract class S3StorageWriter[T](
    client:         S3Client,
    bucket:         String,
    val marshaller: Marshaller[T, Array[Byte]]
)(implicit val loggerFactory: JMLoggerFactory) extends StorageWriter[T] with Logging {

  override def put(value: T, path: String): String = {
    val resolvedPath = resolvePath(path)
    log.info(s"Putting to bucket: $bucket, path: $resolvedPath")
    client.put(bucket, resolvedPath, marshaller.marshall(value))
    resolvedPath
  }

  protected def resolvePath(path: String): String
}

abstract class S3StorageReader[T](
    client:           S3Client,
    bucket:           String,
    val unmarshaller: Unmarshaller[T, Array[Byte]]
)(implicit val loggerFactory: JMLoggerFactory) extends StorageReader[T] with Logging {

  override def get(path: String): T = {
    val resolvedPath = resolvePath(path)
    log.info(s"Getting from bucket: $bucket, path: $resolvedPath")
    val bytes = client.get(bucket, resolvedPath)
    unmarshaller.unmarshall(bytes)
  }

  protected def resolvePath(path: String): String
}

class S3StorageCleaner(
    client: S3Client,
    bucket: String
)(implicit val loggerFactory: JMLoggerFactory) extends StorageCleaner with Logging {

  override def deleteRecursively(path: String): Unit = {
    client.deleteRecursively(bucket, path)
  }
}
