package baile.services.images

import java.io.File
import java.nio.file.Files
import java.util.UUID

import akka.event.LoggingAdapter
import akka.stream.Materializer
import akka.stream.alpakka.csv.scaladsl.CsvParsing
import akka.stream.alpakka.s3.{ ApiVersion, MemoryBufferType, S3Attributes, S3Settings }
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{ Sink, Source }
import baile.dao.images.PictureDao.{ AlbumIdIs, MetaUpdateInfo }
import baile.dao.images.PictureDao
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.common.S3Bucket
import baile.domain.images._
import baile.domain.usermanagement.User
import baile.services.common.S3BucketService.BucketDereferenceError
import baile.services.common.{ EntityUpdateFailedException, S3BucketService }
import baile.services.cortex.job.CortexJobService
import baile.services.cortex.job.SupportedCortexJobTypes._
import baile.services.images.ImagesUploadService.ImagesUploadServiceError._
import baile.services.images.ImagesUploadService._
import baile.services.images.PictureService.PictureServiceError
import baile.services.process.ProcessService
import cats.data.EitherT
import cats.implicits._
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicSessionCredentials, DefaultAWSCredentialsProviderChain }
import com.amazonaws.regions.AwsRegionProvider
import com.google.protobuf.ByteString
import com.typesafe.config.Config
import cortex.api.job.album.uploading.{ S3ImagesImportRequest, S3VideoImportRequest }
import cortex.api.job.album.uploading.{ AlbumLabelMode => CortexAlbumLabelMode }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class ImagesUploadService(
  protected val pictureDao: PictureDao,
  protected val albumService: AlbumService,
  protected val pictureService: PictureService,
  protected val commonService: ImagesCommonService,
  protected val cortexJobService: CortexJobService,
  protected val processService: ProcessService,
  protected val s3BucketService: S3BucketService,
  protected val conf: Config
)(implicit val ec: ExecutionContext, val logger: LoggingAdapter) {

  private val csvLinesChunkSize = conf.getInt("album.labels-uploading.csv-lines-chunk-size")

  def importImagesFromS3(
    albumId: String,
    bucket: S3Bucket,
    imagesPath: String,
    labelsCSVPath: Option[String],
    labelsFile: Option[File],
    applyLogTransformation: Option[Boolean]
  )(implicit user: User): Future[Either[ImagesUploadServiceError, WithId[Album]]] = {

    def readLabelFile(fileOption: Option[File]): Future[Option[ByteString]] = fileOption match {
      case Some(file) => Future {
        Some(ByteString.copyFrom(Files.readAllBytes(file.toPath)))
      }
      case None => Future.successful(None)
    }

    def prepareJobRequest(
      album: Album,
      s3BucketInfo: S3Bucket.AccessOptions,
      labelFileContents: Option[ByteString],
      applyLogTransformation: Boolean
    ) =
      S3ImagesImportRequest(
        bucketName = s3BucketInfo.bucketName,
        awsRegion = s3BucketInfo.region,
        awsAccessKey = s3BucketInfo.accessKey.getOrElse(""),
        awsSecretKey = s3BucketInfo.secretKey.getOrElse(""),
        awsSessionToken = s3BucketInfo.sessionToken.getOrElse(""),
        imagesPath = imagesPath,
        labelsCsvPath = labelsCSVPath.getOrElse(""),
        labelsCsvFile = labelFileContents.getOrElse(ByteString.EMPTY),
        targetPrefix = commonService.getImagesPathPrefix(album),
        labelMode = album.labelMode match {
          case AlbumLabelMode.Classification => CortexAlbumLabelMode.CLASSIFICATION
          case AlbumLabelMode.Localization => CortexAlbumLabelMode.LOCALIZATION
        },
        applyLogTransformation = applyLogTransformation
      )

    def startMonitoring(jobId: UUID, album: WithId[Album]): Future[Unit] = {
      processService.startProcess(
        jobId,
        album.id,
        AssetType.Album,
        classOf[ImagesImportFromS3ResultHandler],
        ImagesImportFromS3ResultHandler.Meta(
          albumId = album.id
        ),
        user.id
      ).map(_ => ())
    }

    val result = for {
      album <- EitherT(loadAlbumMandatory(albumId, user))
      _ <- EitherT(ensurePictureOperationAvailable(album))
      bucketInfo <- EitherT(s3BucketService.dereferenceBucket(bucket)).leftMap(BucketError(_))
      labelFileContents <- EitherT.right[ImagesUploadServiceError](readLabelFile(labelsFile))
      jobRequest = prepareJobRequest(
        album.entity,
        bucketInfo,
        labelFileContents,
        applyLogTransformation.getOrElse(true)
      )
      jobId <- EitherT.right[ImagesUploadServiceError](cortexJobService.submitJob(jobRequest, user.id))
      updatedAlbum <- EitherT.right[ImagesUploadServiceError](markAlbumUploading(album, jobId))
      _ <- EitherT.right[ImagesUploadServiceError](startMonitoring(jobId, album))
    } yield updatedAlbum

    result.value
  }

  def importVideoFromS3(
    albumId: String,
    bucket: S3Bucket,
    videoFilePath: String,
    frameRateDivider: Option[Int]
  )(implicit user: User): Future[Either[ImagesUploadServiceError, WithId[Album]]] = {

    val frameCaptureRate = frameRateDivider.getOrElse(1)

    def prepareJobRequest(album: Album, s3BucketInfo: S3Bucket.AccessOptions) =
      S3VideoImportRequest(
        bucketName = s3BucketInfo.bucketName,
        awsRegion = s3BucketInfo.region,
        awsAccessKey = s3BucketInfo.accessKey.getOrElse(""),
        awsSecretKey = s3BucketInfo.secretKey.getOrElse(""),
        awsSessionToken = s3BucketInfo.sessionToken.getOrElse(""),
        videoPath = videoFilePath,
        targetPrefix = commonService.getImagesPathPrefix(album),
        frameCaptureRate = frameCaptureRate
      )

    def startMonitoring(jobId: UUID, album: WithId[Album]): Future[Unit] = {
      processService.startProcess(
        jobId,
        album.id,
        AssetType.Album,
        classOf[VideoImportFromS3ResultHandler],
        VideoImportFromS3ResultHandler.Meta(
          albumId = album.id,
          frameRateDivider = frameCaptureRate
        ),
        user.id
      ).map(_ => ())
    }

    // TODO: check ownership (get from AlbumService)
    val result = for {
      album <- EitherT(loadAlbumMandatory(albumId, user))
      _ <- EitherT(ensureCanUploadVideo(album))
      bucketInfo <- EitherT(s3BucketService.dereferenceBucket(bucket)).leftMap(BucketError(_))
      jobRequest = prepareJobRequest(album.entity, bucketInfo)
      jobId <- EitherT.right[ImagesUploadServiceError](cortexJobService.submitJob(jobRequest, user.id))
      updatedAlbum <- EitherT.right[ImagesUploadServiceError](markAlbumUploading(album, jobId))
      _ <- EitherT.right[ImagesUploadServiceError](startMonitoring(jobId, album))
    } yield updatedAlbum

    result.value
  }

  def importLabelsFromCSVFileS3(
    albumId: String,
    bucket: S3Bucket,
    labelsCSVPath: String
  )(implicit user: User, materializer: Materializer): Future[Either[ImagesUploadServiceError, WithId[Album]]] = {

    def openCSVFile(
      s3BucketInfo: S3Bucket.AccessOptions
    ): Future[Either[ImagesUploadServiceError, Source[akka.util.ByteString, Any]]] = {

      logger.info("Opening input file: {}", labelsCSVPath)

      val s3Settings = {
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

      S3.download(s3BucketInfo.bucketName, labelsCSVPath)
        .withAttributes(S3Attributes.settings(s3Settings))
        .runWith(Sink.head)
        .map {
          case None => ErrorReadingS3File("File not found").asLeft
          case Some((source, _)) => source.asRight
        }
        .recover {
          case ex: AmazonServiceException => ErrorReadingS3File(ex.getErrorMessage).asLeft
        }
    }

    val result = for {
      album <- EitherT(loadAlbumMandatory(albumId, user))
      _ <- EitherT(ensurePictureOperationAvailable(album))
      bucketInfo <- EitherT(s3BucketService.dereferenceBucket(bucket)).leftMap(BucketError(_))
      csvFile <- EitherT(openCSVFile(bucketInfo))
      _ <- EitherT.right[ImagesUploadServiceError](parseCSVAndUpdate(album, csvFile))
    } yield album

    result.value
  }

  def importLabelsFromCSVFile(
    albumId: String,
    csvFile: Source[akka.util.ByteString, Any]
  )(implicit user: User, materializer: Materializer): Future[Either[ImagesUploadServiceError, WithId[Album]]] = {

    val result = for {
      album <- EitherT(loadAlbumMandatory(albumId, user))
      _ <- EitherT(ensurePictureOperationAvailable(album))
      _ <- EitherT.right[ImagesUploadServiceError](parseCSVAndUpdate(album, csvFile))
    } yield album

    result.value
  }

  private def loadAlbumMandatory(
    albumId: String,
    user: User
  ): Future[Either[ImagesUploadServiceError, WithId[Album]]] = {
    albumService.get(albumId)(user).map {
      _.leftMap(_ => AlbumNotFoundError(albumId))
    }
  }

  private def markAlbumUploading(
    album: WithId[Album],
    jobId: UUID
  ): Future[WithId[Album]] = {
    albumService.update(album.id, status = AlbumStatus.Uploading, inLibrary = album.entity.inLibrary).map(
      _.getOrElse(throw EntityUpdateFailedException(album.id, album.entity))
    )
  }

  private def ensureOperationAvailableOnAlbum(
    album: WithId[Album],
    errorResponse: String => ImagesUploadServiceError
  )(implicit user: User): Future[Either[ImagesUploadServiceError, Unit]] = {
    pictureService.ensurePictureOperationAvailable(album, user).map(_.leftMap[ImagesUploadServiceError] {
      case PictureServiceError.PictureOperationUnavailable(reason) => errorResponse(reason)
      case error => throw new RuntimeException(s"Unexpected response while performing operation on album: $error")
    })
  }

  private def ensurePictureOperationAvailable(
    album: WithId[Album]
  )(implicit user: User): Future[Either[ImagesUploadServiceError, Unit]] = {
    ensureOperationAvailableOnAlbum(album, PictureOperationUnavailable)
  }

  private def ensureCanUploadVideo(
    album: WithId[Album]
  )(implicit user: User): Future[Either[ImagesUploadServiceError, Unit]] = {

    def ensureAlbumHasNoPictures: Future[Either[ImagesUploadServiceError, Unit]] = {
      pictureDao.count(AlbumIdIs(album.id)).map { count =>
        Either.cond(count == 0, (), VideoUploadUnavailable("album already has pictures"))
      }
    }

    val result = for {
      _ <- EitherT(ensureOperationAvailableOnAlbum(album, VideoUploadUnavailable))
      _ <- EitherT(ensureAlbumHasNoPictures)
    } yield ()

    result.value
  }

  private def parseCSVAndUpdate(
    album: WithId[Album],
    csvFile: Source[akka.util.ByteString, Any]
  )(implicit materializer: Materializer): Future[Set[String]] = {

    def buildMetaUpdateInfo(
      fileName: String,
      tag: => PictureTag,
      processedPicturesFileNames: Set[String],
      restFields: Seq[String]
    ): Option[MetaUpdateInfo] = Try {
      MetaUpdateInfo(
        fileName = fileName,
        tags = Seq(tag),
        meta = pictureMetaFields.zip(restFields).toMap,
        replaceTags = !processedPicturesFileNames.contains(fileName)
      )
    }.toOption

    def parseClassificationRow(row: List[String], processedPicturesFileNames: Set[String]): Option[MetaUpdateInfo] =
      row match {
        case fileName :: label :: rest =>
          buildMetaUpdateInfo(fileName, PictureTag(label), processedPicturesFileNames, rest)
        case _ =>
          None
      }

    def parseLocalizationRow(row: List[String], processedPicturesFileNames: Set[String]): Option[MetaUpdateInfo] =
      row match {
        case fileName :: label :: xMin :: yMin :: xMax :: yMax :: rest =>
          buildMetaUpdateInfo(
            fileName,
            PictureTag(
              label = label,
              area = Some(PictureTagArea(
                left = xMin.toInt,
                top = yMin.toInt,
                width = xMax.toInt - xMin.toInt,
                height = yMax.toInt - yMin.toInt
              ))
            ),
            processedPicturesFileNames,
            rest
          )
        case _ =>
          None
      }

    csvFile
      .via(CsvParsing.lineScanner())
      .map(_.map(_.utf8String))
      .grouped(csvLinesChunkSize)
      .runFoldAsync(Set.empty[String]) { (processedPictures, rows) =>
        val parser: List[String] => Option[MetaUpdateInfo] = album.entity.labelMode match {
          case AlbumLabelMode.Classification => parseClassificationRow(_, processedPictures)
          case AlbumLabelMode.Localization => parseLocalizationRow(_, processedPictures)
        }
        val updates = rows
          // step 1: try parsing rows
          .map(parser)
          // step 2: filter out failures
          .collect {
            case Some(metaUpdateInfo) => metaUpdateInfo
          }
          // step 3: combine rows for the same picture
          .foldLeft(Map.empty[String, MetaUpdateInfo]) {
            case (acc, item) =>
              val updateInfo = acc.get(item.fileName) match {
                case Some(oldItem) =>
                  oldItem.copy(
                    tags = oldItem.tags ++ item.tags,
                    meta = item.meta ++ item.meta
                  )
                case None =>
                  item
              }
              acc + (item.fileName -> updateInfo)
          }.values.toSeq

        pictureDao.updateMeta(album.id, updates).map { _ =>
          processedPictures ++ updates.map(_.fileName)
        }
      }
  }

  private val pictureMetaFields = List(
    "originalImageChipURL",
    "grazingAngle",
    "layoverAngle",
    "azimuthAngle",
    "sampleSpacing",
    "scaleFactor",
    "dateTime",
    "location"
  )

}

object ImagesUploadService {

  sealed trait ImagesUploadServiceError

  object ImagesUploadServiceError {
    case class BucketError(error: BucketDereferenceError) extends ImagesUploadServiceError
    case class AlbumNotFoundError(albumId: String) extends ImagesUploadServiceError
    case object AlbumLabelModeNotSupported extends ImagesUploadServiceError
    case class ErrorReadingS3File(reason: String) extends ImagesUploadServiceError
    case class PictureOperationUnavailable(reason: String) extends ImagesUploadServiceError
    case class VideoUploadUnavailable(reason: String) extends ImagesUploadServiceError
  }

}
