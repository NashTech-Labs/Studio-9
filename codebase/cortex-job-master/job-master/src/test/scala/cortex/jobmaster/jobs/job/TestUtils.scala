package cortex.jobmaster.jobs.job

import java.nio.file.{ Files, Paths }

import cortex.io.S3Client

object TestUtils {

  /**
   *
   * @param l
   * @return
   */
  def getMean(l: Seq[Double]): Double = {
    val (total, count) = l.foldLeft(0.0, 0) {
      case ((t, c), x) => (t + x, c + 1)
    }
    total / count
  }

  def getBytesFromS3(s3Client: S3Client, bucket: String, filePath: String): Array[Byte] = {
    Option(Files.readAllBytes(Paths.get(filePath))).getOrElse(Array.empty[Byte])
  }

  def copyToS3(s3Client: S3Client, bucket: String, key: String, filePath: String): Unit = {
    val bytes = Option(Files.readAllBytes(Paths.get(filePath))).getOrElse(Array.empty[Byte])
    s3Client.put(
      bucket   = bucket,
      filename = key,
      payload  = bytes
    )
  }
}
