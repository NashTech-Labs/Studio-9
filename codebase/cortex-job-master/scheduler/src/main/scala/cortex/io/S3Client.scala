package cortex.io

import java.io.ByteArrayInputStream

import com.amazonaws.auth.{ AWSCredentials, AWSStaticCredentialsProvider, BasicAWSCredentials, BasicSessionCredentials }
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.AmazonS3ClientBuilder._
import com.amazonaws.services.s3.model.{ ListObjectsRequest, ObjectMetadata, S3ObjectSummary }
import cortex.io.S3Client.S3File
import org.apache.commons.io.IOUtils

import scala.collection.JavaConverters._
import scala.annotation.tailrec

class S3Client(
    val accessKey:    String,
    val secretKey:    String,
    val region:       String,
    val sessionToken: Option[String] = None,
    val endpointUrl:  Option[String] = None
) {
  private lazy val credentials: AWSCredentials = sessionToken.fold {
    new BasicAWSCredentials(accessKey, secretKey).asInstanceOf[AWSCredentials]
  } { token =>
    new BasicSessionCredentials(accessKey, secretKey, token)
  }

  private lazy val s3ClientBuilderBase = standard()
    .withRegion(region)
    .withPathStyleAccessEnabled(true)
    .disableChunkedEncoding()

  private lazy val s3ClientBuilder = {
    if (accessKey.isEmpty && secretKey.isEmpty) {
      s3ClientBuilderBase
    } else {
      s3ClientBuilderBase.withCredentials(new AWSStaticCredentialsProvider(credentials))
    }
  }

  //to make this class stubble in tests
  Option(endpointUrl).flatten.foreach(e => {
    s3ClientBuilder.withEndpointConfiguration(new EndpointConfiguration(e, null))
  })

  private lazy val s3Client = s3ClientBuilder.build()

  private def startsWithBasePath(basePath: String, path: String): Boolean = {
    path.split("/")
      .zip(basePath.split("/"))
      .forall { case (a, b) => a == b }
  }

  def getFiles(bucket: String, path: Option[String]): Seq[S3File] = {
    val filter = (file: S3ObjectSummary) => {
      // the purpose is to skip all intermediate folders
      // leaving only files that starts with prefix
      !file.getKey.endsWith("/") && path.forall(startsWithBasePath(_, file.getKey))
    }

    extractObjects(bucket, path, filter).map(x => S3File(x.getKey, x.getSize))
  }

  def put(bucket: String, filename: String, payload: Array[Byte]): Unit = {
    val stream = new ByteArrayInputStream(payload)
    val meta = new ObjectMetadata
    meta.setContentLength(payload.length.toLong)
    s3Client.putObject(bucket, filename, stream, meta)
  }

  def get(bucket: String, path: String): Array[Byte] = {
    val obj = s3Client.getObject(bucket, path).getObjectContent
    IOUtils.toByteArray(obj)
  }

  def copy(srcBucket: String, srcPath: String, dstBucket: String, dstPath: String): Unit = {
    s3Client.copyObject(
      srcBucket, srcPath,
      dstBucket, dstPath
    )
  }

  def deleteRecursively(bucket: String, path: String): Unit = {
    val objects = extractObjects(bucket, Some(path))
      .filter(x => startsWithBasePath(path, x.getKey))

    //deleting files
    objects
      .filterNot(_.getKey.endsWith("/"))
      .foreach(x => s3Client.deleteObject(bucket, x.getKey))

    //deleting folder
    objects
      .filter(_.getKey.endsWith("/"))
      .foreach(x => s3Client.deleteObject(bucket, x.getKey))
  }

  @tailrec
  final def extractObjects(
    bucketName: String,
    prefix:     Option[String],
    filter:     S3ObjectSummary => Boolean = _ => true,
    marker:     Option[String]             = None,
    acc:        Seq[S3ObjectSummary]       = Seq.empty[S3ObjectSummary]
  ): Seq[S3ObjectSummary] = {
    val req = new ListObjectsRequest()
    req.setBucketName(bucketName)
    prefix.foreach(req.setPrefix)
    marker.foreach(req.setMarker)

    val objects = s3Client.listObjects(req)
    val list = objects.getObjectSummaries.asScala.toList

    if (objects.isTruncated) {
      extractObjects(
        bucketName,
        prefix,
        filter,
        list.lastOption.map(_.getKey),
        acc ++ list.filter(filter)
      )
    } else {
      acc ++ list.filter(filter)
    }
  }
}

object S3Client {
  case class S3File(filepath: String, fileSizeInBytes: Long)
}
