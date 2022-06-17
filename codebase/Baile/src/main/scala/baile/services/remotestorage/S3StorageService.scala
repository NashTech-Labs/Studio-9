package baile.services.remotestorage

import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.Date

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.alpakka.s3.{ ApiVersion, MemoryBufferType, S3Attributes, S3Settings }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import baile.domain.common.S3Bucket.AccessOptions
import baile.domain.remotestorage.{ S3TemporaryCredentials, TemporaryCredentials }
import baile.services.common.S3BucketService
import com.amazonaws.auth.{
  AWSStaticCredentialsProvider,
  BasicAWSCredentials,
  BasicSessionCredentials,
  DefaultAWSCredentialsProviderChain
}
import com.amazonaws.regions.AwsRegionProvider
import baile.daocommons.sorting.{ Direction, Field, SortBy }
import baile.domain.usermanagement.User
import baile.services.remotestorage.sorting.{ Filename, Filesize, LastModified }
import cats.kernel.Order
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{ ListObjectsRequest, ObjectListing }
import com.amazonaws.services.s3.model.{ ObjectMetadata, _ }
import com.amazonaws.services.securitytoken.model.{ AssumeRoleRequest, Credentials }
import com.amazonaws.services.securitytoken.{ AWSSecurityTokenService, AWSSecurityTokenServiceClientBuilder }
import com.amazonaws.util.IOUtils

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

// TODO Move this to cortex common repo and publish once for all the services to use
class S3StorageService(
  s3BucketInfo: AccessOptions,
  s3BucketService: S3BucketService,
  s3AccessPolicyFilePath: String,
  s3CredentialsDuration: Int,
  s3CredentialsRoleArn: String,
  s3ArnPartititon: String
)(implicit materializer: Materializer) extends RemoteStorageService {

  private val bucketName = s3BucketInfo.bucketName
  private val s3Client: AmazonS3 = s3BucketService.prepareS3Client(s3BucketInfo).get
  private val stsClient: AWSSecurityTokenService = prepareAWSSecurityTokenServiceClient(s3BucketInfo).get

  private val s3Settings: S3Settings = {
    val awsCredentialsProvider =
      if (s3BucketInfo.accessKey.isDefined) {
        new AWSStaticCredentialsProvider(new BasicSessionCredentials(
          s3BucketInfo.accessKey.getOrElse(""),
          s3BucketInfo.secretKey.getOrElse(""),
          s3BucketInfo.sessionToken.getOrElse("")
        ))
      } else {
        new DefaultAWSCredentialsProviderChain
      }

    S3Settings(
      bufferType = MemoryBufferType,
      credentialsProvider = awsCredentialsProvider,
      s3RegionProvider = new AwsRegionProvider {
        lazy val getRegion: String = s3BucketInfo.region
      },
      listBucketApiVersion = ApiVersion.ListBucketVersion2
    ).withPathStyleAccess(true)
  }

  override def write(content: Array[Byte], path: String)(implicit ec: ExecutionContext): Future[File] = {
    val stream = new ByteArrayInputStream(content)
    val meta = new ObjectMetadata()
    meta.setContentLength(content.length.toLong)

    Future {
      val result = s3Client.putObject(bucketName, path, stream, meta)
      File(path, result.getMetadata.getContentLength, Instant.now())
    }
  }

  override def doesExist(path: String)(implicit ec: ExecutionContext): Future[Boolean] = Future {
    s3Client.doesObjectExist(bucketName, path)
  }

  override def readMeta(path: String)(implicit ec: ExecutionContext): Future[File] = Future {
    val metadata = s3Client.getObjectMetadata(bucketName, path)
    File(
      path,
      metadata.getContentLength,
      metadata.getLastModified.toInstant
    )
  }

  override def read(path: String)(implicit ec: ExecutionContext): Future[Array[Byte]] =
    for {
      stream <- Future {
        s3Client.getObject(bucketName, path).getObjectContent
      }
      array <- {
        val result = Future {
          IOUtils.toByteArray(stream)
        }
        result.onComplete {
          _ => stream.close()
        }
        result
      }
    } yield array

  override def streamFile(path: String)(implicit ec: ExecutionContext): Future[StreamedFile] =
    Future(s3Client.getObjectMetadata(bucketName, path)).map { meta =>
      StreamedFile(
        file = File(path = path, size = meta.getContentLength, meta.getLastModified.toInstant),
        content = buildFileSource(path)
      )
    }
  override def copy(sourcePath: String, targetPath: String)(implicit ec: ExecutionContext): Future[File] =
    for {
      _ <- Future(s3Client.copyObject(bucketName, sourcePath, bucketName, targetPath))
      result <- readMeta(targetPath)
    } yield result

  override def copyFrom(
    sourceStorage: RemoteStorageService,
    sourcePath: String,
    targetPath: String
  )(implicit ec: ExecutionContext, materializer: Materializer): Future[File] = sourceStorage match {
    case s3Storage: S3StorageService =>
      val result = for {
        _ <- Future(s3Client.copyObject(s3Storage.bucketName, sourcePath, bucketName, targetPath))
        meta <- readMeta(targetPath)
      } yield meta

      // Just in case
      result recoverWith {
        case _ => super.copyFrom(sourceStorage, sourcePath, targetPath)
      }
    case _ => super.copyFrom(sourceStorage, sourcePath, targetPath)
  }

  override def move(sourcePath: String, targetPath: String)(implicit ec: ExecutionContext): Future[File] =
    for {
      newFile <- copy(sourcePath, targetPath)
      _ <- delete(sourcePath)
    } yield newFile

  override def getExternalUrl(path: String, expiresIn: Long = 60 * 60 * 2): String = {
    val expireAtMillis = ((System.currentTimeMillis + 1000 * expiresIn) / 600000) * 600000
    s3Client.generatePresignedUrl(bucketName, path, new Date(expireAtMillis)).toString
  }

  override def path(base: String, segments: String*): String =
    (base :: segments.toList)
      .map(_.replaceAll("^/|/+$", "")) // trim leading and trailing '/' characters
      .filter(_.length > 0)
      .mkString("/")

  override def split(path: String): Seq[String] =
    path
      .replaceAll("^/|/+$", "") // trim leading and trailing '/' characters
      .split('/')

  override def delete(path: String)(implicit ec: ExecutionContext): Future[Unit] =
    Future {
      s3Client.deleteObject(bucketName, path)
    }

  override def getSink(path: String)(implicit ec: ExecutionContext): Sink[ByteString, Future[File]] = {
    S3.multipartUpload(bucketName, path)
      .withAttributes(S3Attributes.settings(s3Settings))
      .mapMaterializedValue(_.flatMap(_ => readMeta(path)))
  }

  override def createDirectory(path: String)(implicit ec: ExecutionContext): Future[Directory] = Future {
    s3Client.putObject(bucketName, path + "/", "")
    Directory(path)
  }

  override def doesDirectoryExist(path: String)(implicit ec: ExecutionContext): Future[Boolean] = Future {
    s3Client.doesObjectExist(bucketName, path + "/")
  }

  override def moveDirectory(oldPath: String, newPath: String)(implicit ec: ExecutionContext): Future[Directory] =
    for {
      _ <- deleteDirectory(oldPath)
      newDirectory <- createDirectory(newPath)
    } yield newDirectory

  override def deleteDirectory(path: String)(implicit ec: ExecutionContext): Future[Unit] = {

    def deleteObjectsInDirectoryIfPresent(keysToDelete: List[String]): Future[Unit] = {
      if (keysToDelete.nonEmpty) {
        Future {
          val deleteObjectsRequest = new DeleteObjectsRequest(bucketName).withKeys(keysToDelete: _*)
          s3Client.deleteObjects(deleteObjectsRequest)
        }.map(_ => ())
      } else {
        Future.successful(())
      }
    }

    for {
      objectsInDirectory <- listAllObjects(path + "/", recursive = true)
      keysToDelete = objectsInDirectory.map {
        case File(path, _, _) => path
        case Directory(path) => path + "/"
      }
      _ <- deleteObjectsInDirectoryIfPresent(keysToDelete)
      _ <- Future {
        s3Client.deleteObject(bucketName, path + "/")
      }
    } yield ()
  }

  override def listDirectory(
    path: String,
    recursive: Boolean
  )(implicit ec: ExecutionContext): Future[List[StoredObject]] =
    listAllObjects(path + "/", recursive)

  override def getTemporaryCredentials(
    path: String,
    user: User
  )(implicit ec: ExecutionContext): Future[TemporaryCredentials] = {
    Future {
      val policy = scala.io.Source.fromResource(s3AccessPolicyFilePath).mkString
        .replace("#ARN_PARTITION", s3ArnPartititon)
        .replace("#PATH", path)
        .replace("#BUCKET_NAME", bucketName)
      val assumeRoleRequest = new AssumeRoleRequest()
        .withRoleArn(s3CredentialsRoleArn)
        .withRoleSessionName(user.email.replaceAll("\\+","_")) // username is not unique throughout the system
        .withPolicy(policy)
        .withDurationSeconds(s3CredentialsDuration)
      val assumeRoleResponse = stsClient.assumeRole(assumeRoleRequest)
      val accessParams: Credentials = assumeRoleResponse.getCredentials
      S3TemporaryCredentials(
        region = s3BucketInfo.region,
        bucketName = bucketName,
        accessKey = accessParams.getAccessKeyId,
        secretKey = accessParams.getSecretAccessKey,
        sessionToken = accessParams.getSessionToken
      )
    }
  }

  override def streamFiles(path: String)(implicit ec: ExecutionContext): Future[Source[StreamedFile, NotUsed]] =
    listAllItems(path + "/", recursive = false) { listing: ObjectListing =>
      import scala.collection.JavaConverters._
      listing.getObjectSummaries.asScala.collect { case summary if !summary.getKey.endsWith("/") =>
        StreamedFile(
          File(summary.getKey, summary.getSize, summary.getLastModified.toInstant),
          buildFileSource(summary.getKey)
        )
      }.toList
    }

  override def listFiles(
    path: String,
    page: Int,
    pageSize: Int,
    sortBy: Option[SortBy],
    search: Option[String],
    recursive: Boolean
  )(implicit ec: ExecutionContext): Future[(List[File], Int)] = {

    def paginateFiles(files: List[File]): List[File] = {
      files.slice(pageSize * (page - 1), pageSize * page)
    }

    def orderFiles(files: List[File], sortBy: SortBy): List[File] = {

      def buildOrder(field: Field, direction: Direction): Order[File] = {
        val order: Order[File] = field match {
          case Filename => Order.by(_.path)
          case Filesize => Order.by(_.size)
          case LastModified => Order.by[File, Instant](_.lastModified)(
            Order.fromOrdering(implicitly[Ordering[Instant]])
          )
        }
        direction match {
          case Direction.Ascending => order
          case Direction.Descending => Order.reverse(order)
        }
      }

      val order = sortBy
        .fields
        .toList
        .map { case (field, direction) => buildOrder(field, direction) }
        .combineAll(Order.whenEqualMonoid[File])

      files.sorted(order.toOrdering)
    }

    listAllObjects(path + "/", recursive = recursive, search = search).map { objects =>
      val files = objects.collect { case file: File => file }
      val orderedFiles = sortBy.fold(files)(orderFiles(files, _))
      val paginatedFiles = paginateFiles(orderedFiles)
      (paginatedFiles, files.size)
    }
  }

  private def listAllObjects(
    prefix: String,
    recursive: Boolean,
    search: Option[String] = None
  )(implicit ec: ExecutionContext): Future[List[StoredObject]] = {

    import scala.collection.JavaConverters._

    def fileObjectKeysPredicate(key: String): Boolean =
      if (recursive) !key.endsWith("/")
      else key != prefix // exclude empty file which represents folder

    def searchPredicate(key: String): Boolean = search match {
      case Some(value) => key.stripPrefix(prefix).matches("(?i:.*" + value + ".*)")
      case None => true
    }

    def buildObjects(listing: ObjectListing): List[StoredObject] = {
      val filesBatch = listing.getObjectSummaries.asScala.collect {
        case objectSummary if fileObjectKeysPredicate(objectSummary.getKey) && searchPredicate(objectSummary.getKey) =>
          File(objectSummary.getKey, objectSummary.getSize, objectSummary.getLastModified.toInstant)
      }.toList
      val directoriesKeys =
        if (recursive) {
          listing.getObjectSummaries.asScala.collect {
            case objectSummary
              if objectSummary.getKey.endsWith("/")
                && objectSummary.getKey != prefix
                && searchPredicate(objectSummary.getKey) =>
              objectSummary.getKey
          }
        } else {
          listing.getCommonPrefixes.asScala
        }
      val directoriesBatch = directoriesKeys.map(key => Directory(key.stripSuffix("/"))).toList
      filesBatch ++ directoriesBatch
    }

    for {
      source <- listAllItems(prefix, recursive)(buildObjects)
      result <- source.runWith(Sink.seq)
    } yield result.toList

  }

  private def listAllItems[T](
    prefix: String,
    recursive: Boolean
  )(buildItems: ObjectListing => List[T])(implicit ec: ExecutionContext): Future[Source[T, NotUsed]] =
  // scalastyle:off null
    for {
      firstListing <- Future(s3Client.listObjects(new ListObjectsRequest(
        bucketName,
        prefix,
        null,
        if (recursive) null else "/",
        null
      )))
      source = Source.unfoldAsync[Option[ObjectListing], List[T]](Some(firstListing)) {
        case None =>
          Future.successful(None)
        case Some(listing) =>
          val nextChunk = buildItems(listing)
          if (listing.isTruncated) {
            Future {
              Some(Some(s3Client.listNextBatchOfObjects(listing)) -> nextChunk)
            }
          } else {
            Future.successful(Some(None -> nextChunk))
          }
      }
    } yield source.mapConcat(identity)

  // TODO Abstract this and S3BucketService#prepareS3Client
  private def prepareAWSSecurityTokenServiceClient(s3BucketInfo: AccessOptions): Try[AWSSecurityTokenService] = Try {

    val awsSecurityTokenServiceClientBuilderBase = AWSSecurityTokenServiceClientBuilder.standard()
      .withRegion(s3BucketInfo.region)

    val awsSecurityTokenServiceClientBuilder = {
      (s3BucketInfo.accessKey, s3BucketInfo.secretKey) match {
        case (None, None) => awsSecurityTokenServiceClientBuilderBase
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
          awsSecurityTokenServiceClientBuilderBase.withCredentials(new AWSStaticCredentialsProvider(credentials))
        case _ => throw new IllegalArgumentException(
          "AWSSecurityTokenService client could not be created since partial AWS credentials were provided." +
            "You must supply both an access key and secret key, or neither to use IAM roles. Check your configuration."
        )
      }
    }

    awsSecurityTokenServiceClientBuilder.build()
  }

  private def buildFileSource(path: String): Source[ByteString, NotUsed] =
    S3.download(bucketName, path)
      .withAttributes(S3Attributes.settings(s3Settings))
      .collect { case Some(result) => result }
      .flatMapConcat { case (data, _) => data }

}
