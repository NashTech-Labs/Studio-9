package baile.services.common

import akka.event.LoggingAdapter
import baile.daocommons.WithId
import baile.domain.common.S3Bucket
import baile.services.common.S3BucketService.{ BucketDereferenceError, BucketNotFound, EmptyKey, InvalidAWSRegion }
import baile.utils.StringExtensions._
import cats.implicits._
import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials, BasicSessionCredentials }
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{ AmazonS3, AmazonS3ClientBuilder }
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class S3BucketService(
  conf: Config
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) {

  private[services] val storageKeyPrefix: String = conf.getString("aws.key-prefix")

  def listAll(): Future[Seq[WithId[S3Bucket.AccessOptions]]] = Future {
    conf.getConfigList("aws.predefined-buckets").asScala.collect {
      case item if item.hasPath("name") && item.getString("name").nonEmpty =>
        WithId(
          toAccessOptions(item),
          item.getString("id")
        )
    }
  }

  def dereferenceBucket(bucket: S3Bucket): Future[Either[BucketDereferenceError, S3Bucket.AccessOptions]] =
    Future.successful {
      bucket match {
        case options: S3Bucket.AccessOptions =>
          if (options.accessKey.getOrElse("").isEmpty || options.secretKey.getOrElse("").isEmpty) {
            EmptyKey.asLeft
          } else if (!Regions.values.map(_.getName).contains(options.region)) {
            InvalidAWSRegion.asLeft
          } else {
            options.asRight
          }
        case S3Bucket.IdReference(bucketId) =>
          conf.getConfigList("aws.predefined-buckets").asScala.collectFirst {
            case item: Config if item.getString("id") == bucketId => toAccessOptions(item)
          } match {
            case Some(accessOptions) => accessOptions.asRight
            case None => BucketNotFound.asLeft
          }
      }
    }

  def prepareS3Client(s3BucketInfo: S3Bucket.AccessOptions): Try[AmazonS3] = Try {

    val s3ClientBuilderBase = AmazonS3ClientBuilder.standard()
      .withRegion(s3BucketInfo.region)
      .withPathStyleAccessEnabled(true)


    val s3ClientBuilder = {
      (s3BucketInfo.accessKey, s3BucketInfo.secretKey) match {
        case (None, None) => s3ClientBuilderBase
        case (Some(accessKey), Some(secretKey)) =>
          val credentials = s3BucketInfo.sessionToken match {
            case None => new BasicAWSCredentials(
              accessKey,
              secretKey
            )
            case Some(sessionKey) => new BasicSessionCredentials(
              accessKey,
              secretKey,
              sessionKey
            )
          }
          s3ClientBuilderBase.withCredentials(new AWSStaticCredentialsProvider(credentials))
        case _ => throw new IllegalArgumentException("S3 client could not be created since partial AWS credentials " +
          "were provided. You must supply both an access key and secret key, or neither to use IAM roles. " +
          "Check your configuration.")
      }
    }

    s3ClientBuilder.build()
  }

  private def toAccessOptions(item: Config): S3Bucket.AccessOptions = {
    S3Bucket.AccessOptions(
      region = item.getString("region"),
      bucketName = item.getString("name"),
      accessKey = item.getString("access-key").toOption,
      secretKey = item.getString("secret-key").toOption,
      sessionToken = None
    )
  }

}

object S3BucketService {

  sealed trait BucketDereferenceError
  case object BucketNotFound extends BucketDereferenceError
  case object InvalidAWSRegion extends BucketDereferenceError
  case object EmptyKey extends BucketDereferenceError

}
