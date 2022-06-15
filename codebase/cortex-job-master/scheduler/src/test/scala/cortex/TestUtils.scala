package cortex

import java.nio.file.{ Files, Paths }

import cortex.io.S3Client

/**
 * Copied from package job
 */
object TestUtils {

  def copyToS3(s3Client: S3Client, bucket: String, key: String, filePath: String): Unit = {
    val bytes = Option(Files.readAllBytes(Paths.get(filePath))).getOrElse(Array.empty[Byte])
    s3Client.put(
      bucket   = bucket,
      filename = key,
      payload  = bytes
    )
  }
}
