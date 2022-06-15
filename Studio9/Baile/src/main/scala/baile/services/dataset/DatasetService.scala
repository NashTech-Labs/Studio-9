package baile.services.dataset

import java.time.Instant
import java.util.UUID

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import baile.dao.asset.Filters.{ NameIs, OwnerIdIs }
import baile.dao.dataset.DatasetDao
import baile.daocommons.WithId
import baile.daocommons.filters.{ Filter, IdIs }
import baile.daocommons.sorting.Field
import baile.domain.asset.AssetType
import baile.domain.common.S3Bucket
import baile.domain.dataset.DatasetStatus.Active
import baile.domain.dataset.{ Dataset, DatasetStatus }
import baile.domain.usermanagement.User
import baile.services.asset.AssetService
import baile.services.asset.AssetService.{ WithOwnershipTransfer, WithProcess, WithSharedAccess }
import baile.services.asset.AssetService.{
  AssetCreateErrors,
  AssetCreateParams,
  WithCreate,
  WithNestedUsageTracking,
  WithProcess,
  WithSharedAccess
}
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.EntityService.WithSortByField
import baile.services.common.S3BucketService
import baile.services.common.S3BucketService.BucketDereferenceError
import baile.services.cortex.job.CortexJobService
import baile.services.dataset.DatasetService.DatasetServiceError
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.services.remotestorage.sorting.{ Filename, Filesize, LastModified }
import baile.services.remotestorage.{ File, RemoteStorageService, StreamedFile }
import baile.utils.streams.InputFileSource
import baile.utils.validation.Option._
import cats.data.EitherT
import cats.implicits._
import cortex.api.job.dataset.{ S3DatasetExportRequest, S3DatasetImportRequest }

import scala.concurrent.{ ExecutionContext, Future }

class DatasetService(
  protected val dao: DatasetDao,
  protected val projectService: ProjectService,
  protected val fileStorage: RemoteStorageService,
  protected val fileStoragePrefix: String,
  protected val assetSharingService: AssetSharingService,
  protected val s3BucketService: S3BucketService,
  protected val processService: ProcessService,
  protected val cortexJobService: CortexJobService
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter,
  val materializer: Materializer
) extends AssetService[Dataset, DatasetServiceError]
  with WithSortByField[Dataset, DatasetServiceError]
  with WithProcess[Dataset, DatasetServiceError]
  with WithSharedAccess[Dataset, DatasetServiceError]
  with WithOwnershipTransfer[Dataset]
  with WithNestedUsageTracking[Dataset, DatasetServiceError]
  with WithCreate[Dataset, DatasetServiceError, DatasetServiceError] {

  import DatasetServiceError._

  override val assetType: AssetType = AssetType.Dataset
  override val forbiddenError: DatasetServiceError = AccessDenied
  override val sortingFieldNotFoundError: DatasetServiceError = SortingFieldUnknown
  override val notFoundError: DatasetServiceError = DatasetNotFound
  override val inUseError: DatasetServiceError = DatasetInUse

  override protected val createErrors: AssetCreateErrors[DatasetServiceError] = DatasetServiceError
  override protected val findField: String => Option[Field] = Map(
    "name" -> DatasetDao.Name,
    "created" -> DatasetDao.Created,
    "updated" -> DatasetDao.Updated
  ).get

  private val findFieldForFile: String => Option[Field] = Map(
    "filename" -> Filename,
    "filesize" -> Filesize,
    "modified" -> LastModified
  ).get

  override def updateOwnerId(dataset: Dataset, ownerId: UUID): Dataset = dataset.copy(ownerId = ownerId)

  def create(
    name: Option[String],
    description: Option[String]
  )(implicit user: User): Future[Either[DatasetServiceError, WithId[Dataset]]] = {

    def createDataset(createParams: AssetCreateParams): Future[WithId[Dataset]] = {
      val now = Instant.now()
      dao.create(id =>
        Dataset(
          name = createParams.name,
          created = now,
          updated = now,
          ownerId = user.id,
          status = DatasetStatus.Active,
          description = description,
          basePath = s"datasets/$id"
        )
      )
    }

    val result = for {
      createParams <- validateAndGetAssetCreateParams(name, None)
      project <- EitherT.right[DatasetServiceError](createDataset(createParams))
    } yield project

    result.value
  }

  def update(
    id: String,
    newName: Option[String],
    newDescription: Option[String]
  )(implicit user: User): Future[Either[DatasetServiceError, WithId[Dataset]]] = {

    this.update(
      id,
      _ => newName.validate(name =>
        ensureValidAndUniqueName(name, NameIs(name) && OwnerIdIs(user.id) && !IdIs(id))
      ),
      dataset => dataset.copy(
        name = newName.getOrElse(dataset.name),
        updated = Instant.now(),
        description = newDescription orElse dataset.description
      )
    )
  }

  def upload(
    datasetId: String,
    inputFileSources: Source[InputFileSource, Any]
  )(implicit user: User): Future[Either[DatasetServiceError, Unit]] = {

    val result = for {
      datasetWithId <- EitherT(get(datasetId))
      _ <- EitherT.right[DatasetServiceError] {
        inputFileSources.runFoldAsync(()) { (_, inputFileSources) =>
          val filePath = buildFullStoragePath(datasetWithId.entity, inputFileSources.fileName)
          fileStorage.write(inputFileSources.source, filePath).map(_ => ())
        }
      }
    } yield ()

    result.value
  }

  def removeFile(
    id: String,
    datasetFileName: String
  )(implicit user: User): Future[Either[DatasetServiceError, Unit]] = {
    val result = for {
      dataset <- EitherT(get(id))
      _ <- EitherT.cond[Future](dataset.entity.status == Active, (), DatasetIsNotActive)
      fullPath = fileStorage.path(buildDatasetStoragePrefix(dataset.entity), datasetFileName)
      fileExists <- EitherT.right[DatasetServiceError](fileStorage.doesExist(fullPath))
      _ <- EitherT {
        if (fileExists) fileStorage.delete(fullPath).map(_.asRight[DatasetServiceError])
        else Future.successful(DatasetServiceError.FileNotFound.asLeft)
      }
    } yield ()
    result.value
  }

  def listFiles(
    id: String,
    page: Int,
    pageSize: Int,
    orderBy: Seq[String],
    sharedResourceId: Option[String] = None,
    search: Option[String]
  )(implicit user: User): Future[Either[DatasetServiceError, (List[File], Int)]] = {

    val result = for {
      datasetWithId <- EitherT(get(id, sharedResourceId))
      sortBy <- EitherT.fromEither[Future](prepareSortBy(orderBy, findFieldForFile))
      datasetPath = buildDatasetStoragePrefix(datasetWithId.entity)
      filesAndCount <- EitherT.right[DatasetServiceError](
        fileStorage.listFiles(datasetPath, page, pageSize, sortBy, search, recursive = true)
      )
    } yield {
      val (files, count) = filesAndCount
      val datasetFiles = files.map { file =>
        file.updatePath(file.path.stripPrefix(datasetPath + "/"))
      }
      (datasetFiles, count)
    }

    result.value
  }

  def importDatasetFromS3(
    datasetId: String,
    bucket: S3Bucket,
    path: String
  )(implicit user: User): Future[Either[DatasetServiceError, WithId[Dataset]]] = {

    def prepareJobRequest(
      dataset: Dataset,
      s3BucketInfo: S3Bucket.AccessOptions
    ) =
      S3DatasetImportRequest(
        bucketName = s3BucketInfo.bucketName,
        awsRegion = s3BucketInfo.region,
        awsAccessKey = Some(s3BucketInfo.accessKey.getOrElse("")),
        awsSecretKey = Some(s3BucketInfo.secretKey.getOrElse("")),
        awsSessionToken = Some(s3BucketInfo.sessionToken.getOrElse("")),
        datasetPath = path,
        targetPrefix = buildDatasetStoragePrefix(dataset)
      )

    def startMonitoring(jobId: UUID, dataset: WithId[Dataset]): Future[Unit] = {
      processService.startProcess(
        jobId,
        dataset.id,
        AssetType.Dataset,
        classOf[DatasetImportFromS3ResultHandler],
        DatasetImportFromS3ResultHandler.Meta(
          datasetId = dataset.id
        ),
        user.id
      ).map(_ => ())
    }

    val result = for {
      dataset <- EitherT(get(datasetId))
      _ <- EitherT.cond[Future](dataset.entity.status == DatasetStatus.Active, (), DatasetIsNotActive)
      bucketInfo <- EitherT(s3BucketService.dereferenceBucket(bucket)).leftMap(BucketError)
      jobRequest = prepareJobRequest(
        dataset.entity,
        bucketInfo
      )
      jobId <- EitherT.right[DatasetServiceError](cortexJobService.submitJob(jobRequest, user.id))
      updatedDataset <- EitherT(update(dataset.id, _.copy(status = DatasetStatus.Importing)))
      _ <- EitherT.right[DatasetServiceError](startMonitoring(jobId, dataset))
    } yield updatedDataset

    result.value
  }

  def exportDatasetToS3(
    datasetId: String,
    bucket: S3Bucket,
    targetPath: String
  )(implicit user: User): Future[Either[DatasetServiceError, WithId[Dataset]]] = {

    def prepareJobRequest(
      dataset: Dataset,
      s3BucketInfo: S3Bucket.AccessOptions
    ) =
      S3DatasetExportRequest(
        bucketName = s3BucketInfo.bucketName,
        awsRegion = s3BucketInfo.region,
        awsAccessKey = Some(s3BucketInfo.accessKey.getOrElse("")),
        awsSecretKey = Some(s3BucketInfo.secretKey.getOrElse("")),
        awsSessionToken = Some(s3BucketInfo.sessionToken.getOrElse("")),
        datasetPath = buildDatasetStoragePrefix(dataset),
        targetPrefix = targetPath
      )

    def startMonitoring(jobId: UUID, dataset: WithId[Dataset]): Future[Unit] = {
      processService.startProcess(
        jobId,
        dataset.id,
        AssetType.Dataset,
        classOf[DatasetExportToS3ResultHandler],
        DatasetExportToS3ResultHandler.Meta(
          datasetId = dataset.id
        ),
        user.id
      ).map(_ => ())
    }


    val result = for {
      dataset <- EitherT(get(datasetId))
      _ <- EitherT.cond[Future](dataset.entity.status == DatasetStatus.Active, (), DatasetIsNotActive)
      bucketInfo <- EitherT(s3BucketService.dereferenceBucket(bucket)).leftMap(BucketError)
      jobRequest = prepareJobRequest(
        dataset.entity,
        bucketInfo
      )
      jobId <- EitherT.right[DatasetServiceError](cortexJobService.submitJob(jobRequest, user.id))
      updatedDataset <- EitherT(update(dataset.id, _.copy(status = DatasetStatus.Exporting)))
      _ <- EitherT.right[DatasetServiceError](startMonitoring(jobId, dataset))
    } yield updatedDataset

    result.value
  }

  def download(
    id: String,
    fileNames: List[String],
    sharedResourceId: Option[String] = None
  )(implicit user: User): Future[Either[DatasetServiceError, Source[StreamedFile, NotUsed]]] = {
    val result = for {
      dataset <- EitherT(get(id, sharedResourceId))
      filesPrefix = buildDatasetStoragePrefix(dataset.entity)
      source <- EitherT.right[DatasetServiceError] {
        if (fileNames.isEmpty) {
          fileStorage.streamFiles(filesPrefix)
        } else {
          def iterator(fileNames: List[String]): Future[Option[(List[String], StreamedFile)]] = fileNames match {
            case Nil => Future.successful(None)
            case fileName :: rest => fileStorage.streamFile(fileStorage.path(filesPrefix, fileName)).map { file =>
              Some(rest -> file)
            } recoverWith {
              case _ => iterator(rest)
            }
          }

          Future.successful(Source.unfoldAsync(fileNames)(iterator))
        }
      }
    } yield source.map { streamedFile =>
      val file = streamedFile.file.updatePath(streamedFile.file.path.stripPrefix(filesPrefix + "/"))
      streamedFile.copy(file = file)
    }

    result.value
  }

  def signFiles(
    id: String,
    files: Seq[File],
    sharedResourceId: Option[String] = None
  )(implicit user: User): Future[Either[DatasetServiceError, Seq[String]]] = {

    val result = for {
      datasetWithId <- EitherT(get(id, sharedResourceId))
      datasetPath = buildDatasetStoragePrefix(datasetWithId.entity)
    } yield {
      files.map { file =>
        val fullPath: String = fileStorage.path(
          datasetPath,
          file.path
        )
        fileStorage.getExternalUrl(fullPath)
      }
    }

    result.value
  }

  private def ensureNameUnique(filter: Filter, datasetName: String): Future[Either[DatasetServiceError, Unit]] = {
    dao.count(filter).map { count =>
      if (count > 0) NameIsTaken.asLeft else ().asRight
    }
  }

  private def ensureValidAndUniqueName(
    name: String,
    filter: Filter
  ): Future[Either[DatasetServiceError, Unit]] = {
    val result = for {
      _ <- EitherT.cond[Future](
        name.nonEmpty,
        (),
        EmptyDatasetName
      )
      _ <- EitherT(ensureNameUnique(filter, name))
    } yield ()
    result.value
  }

  private[services] def buildFullStoragePath(dataset: Dataset, path: String): String =
    fileStorage.path(buildDatasetStoragePrefix(dataset), path)

  private def buildDatasetStoragePrefix(dataset: Dataset): String =
    fileStorage.path(fileStoragePrefix, dataset.basePath)
}

object DatasetService {

  sealed trait DatasetServiceError

  object DatasetServiceError extends AssetCreateErrors[DatasetServiceError] {

    case object DatasetNotFound extends DatasetServiceError

    case object DatasetInUse extends DatasetServiceError

    case class BucketError(error: BucketDereferenceError) extends DatasetServiceError

    case object AccessDenied extends DatasetServiceError

    case object SortingFieldUnknown extends DatasetServiceError

    case object EmptyDatasetName extends DatasetServiceError

    case object NameNotSpecified extends DatasetServiceError

    case object NameIsTaken extends DatasetServiceError

    case object DatasetIsNotActive extends DatasetServiceError

    case object FileNotFound extends DatasetServiceError

    override val nameNotSpecifiedError: DatasetServiceError = NameNotSpecified
    override val emptyNameError: DatasetServiceError = EmptyDatasetName

    override def nameAlreadyExistsError(name: String): DatasetServiceError = NameIsTaken
  }

}
