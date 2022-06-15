package baile.services.tabular.model

import java.time.Instant
import java.util.UUID

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.dao.tabular.model.TabularModelDao
import baile.daocommons.WithId
import baile.daocommons.sorting.Field
import baile.domain.asset.AssetType
import baile.domain.common.{ ClassReference, Version }
import baile.domain.tabular.model.{ TabularModel, TabularModelStatus }
import baile.domain.usermanagement.User
import baile.services.asset.AssetService
import baile.services.asset.AssetService.{
  WithOwnershipTransfer,
  AssetCreateErrors,
  AssetCreateParams,
  WithCreate,
  WithNestedUsageTracking,
  WithProcess,
  WithSharedAccess
}
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.EntityService.WithSortByField
import baile.services.common.MLEntityExportImportService
import baile.services.common.MLEntityExportImportService.{ EntityFileSavedResult, EntityImportError }
import baile.services.cortex.job.CortexJobService
import baile.services.dcproject.DCProjectPackageService
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.services.remotestorage.RemoteStorageService
import baile.services.table.TableService
import baile.services.tabular.model.TabularModelService.TabularModelImportError.{
  ImportedMetaFormatError,
  ImportedMetaIsTooBig,
  InLibraryWrongFormat
}
import baile.services.tabular.model.TabularModelService.TabularModelServiceError._
import baile.services.tabular.model.TabularModelService._
import baile.services.tabular.model.util.export.TabularModelExportMeta
import baile.utils.validation.Option._
import cats.data.EitherT
import cats.implicits._
import cortex.api.job.tabular.TabularModelImportRequest
import cortex.api.job.common.{ ClassReference => CortexClassReference }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class TabularModelService(
  protected val dao: TabularModelDao,
  protected val processService: ProcessService,
  protected val cortexJobService: CortexJobService,
  protected val tableService: TableService,
  protected val tabularModelCommonService: TabularModelCommonService,
  protected val assetSharingService: AssetSharingService,
  protected val exportImportService: MLEntityExportImportService,
  protected val projectService: ProjectService,
  protected val packageService: DCProjectPackageService,
  protected val mlEntitiesStorage: RemoteStorageService
)(
  implicit val logger: LoggingAdapter,
  val ec: ExecutionContext
) extends AssetService[TabularModel, TabularModelServiceError]
  with WithSortByField[TabularModel, TabularModelServiceError]
  with WithProcess[TabularModel, TabularModelServiceError]
  with WithSharedAccess[TabularModel, TabularModelServiceError]
  with WithNestedUsageTracking[TabularModel, TabularModelServiceError]
  with WithOwnershipTransfer[TabularModel]
  with WithCreate[TabularModel, TabularModelServiceError, TabularModelImportError] {

  override val forbiddenError: TabularModelServiceError = TabularModelServiceError.AccessDenied
  override val sortingFieldNotFoundError: TabularModelServiceError = SortingFieldUnknown
  override val notFoundError: TabularModelServiceError = TabularModelServiceError.ModelNotFound
  override val assetType: AssetType = AssetType.TabularModel
  override val inUseError: TabularModelServiceError = TabularModelServiceError.TabularModelInUse

  override protected val createErrors: AssetCreateErrors[TabularModelImportError] = TabularModelImportError
  override protected val findField: String => Option[Field] = Map(
    "name" -> TabularModelDao.Name,
    "created" -> TabularModelDao.Created,
    "updated" -> TabularModelDao.Updated
  ).get

  override def updateOwnerId(tabularModel: TabularModel, ownerId: UUID): TabularModel = {
    tabularModel.copy(ownerId = ownerId)
  }

  def save(
    id: String,
    name: String,
    description: Option[String]
  )(implicit user: User): Future[Either[TabularModelServiceError, WithId[TabularModel]]] = {
    update(
      id,
      _ => validateAssetName(
        name,
        Option(id),
        TabularModelServiceError.EmptyTabularModelName,
        TabularModelServiceError.NameIsTaken
      ),
      tabularModel => tabularModel.copy(
        name = name,
        description = description orElse tabularModel.description,
        inLibrary = true
      )
    )
  }

  def clone(
    id: String,
    name: String,
    description: Option[String],
    sharedResourceId: Option[String]
  )(implicit user: User): Future[Either[TabularModelServiceError, WithId[TabularModel]]] = {

    val result = for {
      tablularModel <- EitherT(get(id, sharedResourceId))
      _ <- EitherT.fromEither[Future](ensureModelIsActive(tablularModel))
      _ <- EitherT(validateAssetName(name, None, EmptyTabularModelName, ModelNameAlreadyExists))
      dateTime = Instant.now()
      clonedModel <- EitherT.right[TabularModelServiceError](dao.create(id => tablularModel.entity.copy(
        ownerId = user.id,
        name = name,
        created = dateTime,
        updated = dateTime,
        status = TabularModelStatus.Active,
        description = description,
        experimentId = None
      )))
    } yield clonedModel
    result.value
  }

  def update(
    id: String,
    newName: Option[String],
    newDescription: Option[String]
  )(implicit user: User): Future[Either[TabularModelServiceError, WithId[TabularModel]]] = {

    update(
      id,
      _ => newName.validate(name => validateAssetName(
        name,
        Option(id),
        TabularModelServiceError.ModelNameIsEmpty,
        TabularModelServiceError.ModelNameAlreadyExists
      )),
      tabularModel => tabularModel.copy(
        name = newName.getOrElse(tabularModel.name),
        description = newDescription orElse tabularModel.description
      )
    )
  }

  def getStateFileUrl(
    id: String
  )(implicit user: User): Future[Either[TabularModelServiceError, String]] = {
    val result = for {
      model <- EitherT(get(id))
      _ <- EitherT.cond[Future](
        model.entity.status == TabularModelStatus.Active,
        (),
        TabularModelServiceError.ModelNotActive
      )
      modelReference <- EitherT.fromOption[Future](
        model.entity.cortexModelReference,
        TabularModelServiceError.ModelFilePathNotFound: TabularModelServiceError
      )
    } yield mlEntitiesStorage.getExternalUrl(modelReference.cortexFilePath)

    result.value
  }

  def export(
    id: String
  )(implicit user: User): Future[Either[TabularModelServiceError, Source[ByteString, NotUsed]]] = {
    val result = for {
      model <- EitherT(get(id))
      _ <- EitherT.fromEither[Future](ensureModelIsActive(model))
      modelReference <- EitherT.fromOption[Future](
        model.entity.cortexModelReference,
        TabularModelServiceError.CantExportTabularModel: TabularModelServiceError
      )
      packageWithId <- EitherT.right[TabularModelServiceError](
        packageService.loadPackageMandatory(model.entity.classReference.packageId)
      )
      source <- EitherT.right[TabularModelService.TabularModelServiceError](exportImportService.exportEntity(
        modelReference.cortexFilePath,
        TabularModelExportMeta(model.entity, packageWithId)
      ))
    } yield source

    result.value
  }

  def importModel(
    modelFileSource: Source[ByteString, Any],
    paramsF: Future[Map[String, String]]
  )(implicit user: User,  materializer: Materializer): Future[Either[TabularModelImportError, WithId[TabularModel]]] = {

    def saveModel(
      createParams: AssetCreateParams,
      meta: TabularModelExportMeta,
      packageId: String
    ): Future[WithId[TabularModel]] = {
      val now = Instant.now()
      val model = TabularModel(
        ownerId = user.id,
        name = createParams.name,
        created = now,
        updated = now,
        predictorColumns = meta.predictorColumns,
        responseColumn = meta.responseColumn,
        classReference = ClassReference(
          moduleName = meta.classReference.moduleName,
          className = meta.classReference.className,
          packageId = packageId
        ),
        classNames = meta.classNames,
        cortexModelReference = None,
        inLibrary = createParams.inLibrary,
        status = TabularModelStatus.Saving,
        description = meta.description,
        experimentId = None
      )
      dao.create(model).map(WithId(model, _))
    }

    def handleSavedEntityFile(
      entityFileSaveResult: EntityFileSavedResult[TabularModelExportMeta]
    ): Future[Either[TabularModelImportError, WithId[TabularModel]]] = {
      val result = for {
        params <- EitherT.right[TabularModelImportError](paramsF)
        inLibrary <- EitherT.fromEither[Future] {
          params.get("inLibrary") match {
            case None => None.asRight
            case Some(str) =>
              Try(str.toBoolean) match {
                case Failure(_) => InLibraryWrongFormat.asLeft
                case Success(value) => Some(value).asRight
              }
          }
        }
        createParams <- validateAndGetAssetCreateParams(params.get("name"), inLibrary)
        classReference = entityFileSaveResult.meta.classReference
        packageVersion = classReference.packageVersion.map { version =>
          Version(version.major, version.minor, version.patch, version.suffix)
        }
        modelPackage <- EitherT.fromOptionF(
          packageService.getPackageByNameAndVersion(classReference.packageName, packageVersion),
          TabularModelImportError.PackageNotFound(classReference.packageName, packageVersion)
        )
        importJobId <- EitherT.right[TabularModelImportError](cortexJobService.submitJob(
          TabularModelImportRequest(
            path = entityFileSaveResult.filePath,
            modelClassReference = Some(CortexClassReference(
              packageLocation = modelPackage.entity.location,
              className = classReference.className,
              moduleName = classReference.moduleName
            ))
          ),
          user.id
        ))
        model <- EitherT.right[TabularModelImportError](
          saveModel(createParams, entityFileSaveResult.meta, modelPackage.id)
        )
        _ <- EitherT.right[TabularModelImportError](processService.startProcess(
          jobId = importJobId,
          targetId = model.id,
          targetType = AssetType.TabularModel,
          handlerClass = classOf[TabularModelImportResultHandler],
          meta = TabularModelImportResultHandler.Meta(model.id, entityFileSaveResult.filePath),
          userId = user.id
        ))
      } yield model
      result.value
    }

    def translateImportError(error: EntityImportError[TabularModelImportError]): TabularModelImportError = error match {
      case EntityImportError.MetaIsTooBig => ImportedMetaIsTooBig
      case EntityImportError.InvalidMetaFormat(error) => ImportedMetaFormatError(error)
      case EntityImportError.ImportHandlingFailed(e) => e
    }

    exportImportService.importEntity[TabularModelImportError, WithId[TabularModel], TabularModelExportMeta](
      modelFileSource,
      _ => EitherT.fromEither[Future](().asRight),
      handleSavedEntityFile
    ).map(_.leftMap(translateImportError))
  }

  private def ensureModelIsActive(model: WithId[TabularModel]): Either[TabularModelServiceError, Unit] = {
    Either.cond(
      model.entity.status == TabularModelStatus.Active,
      (),
      TabularModelServiceError.ModelNotActive
    )
  }

}

object TabularModelService {

  sealed trait TabularModelImportError

  object TabularModelImportError extends AssetCreateErrors[TabularModelImportError] {
    case object EmptyModelName extends TabularModelImportError
    case object NameIsTaken extends TabularModelImportError
    case object NameNotSpecified extends TabularModelImportError
    case object ImportedMetaIsTooBig extends TabularModelImportError
    case class PackageNotFound(packageName: String, version: Option[Version]) extends TabularModelImportError
    case class ImportedMetaFormatError(error: String) extends TabularModelImportError
    case object InLibraryWrongFormat extends TabularModelImportError

    override val nameNotSpecifiedError: TabularModelImportError = NameNotSpecified
    override val emptyNameError: TabularModelImportError = EmptyModelName

    override def nameAlreadyExistsError(name: String): TabularModelImportError = NameIsTaken
  }

  sealed trait TabularModelServiceError

  object TabularModelServiceError {
    case object AccessDenied extends TabularModelServiceError
    case object ModelNotFound extends TabularModelServiceError
    case object ModelNameIsEmpty extends TabularModelServiceError
    case object ModelNameAlreadyExists extends TabularModelServiceError
    case object SortingFieldUnknown extends TabularModelServiceError
    case object TabularModelInUse extends TabularModelServiceError
    case object EmptyTabularModelName extends TabularModelServiceError
    case object NameIsTaken extends TabularModelServiceError
    case object ModelFilePathNotFound extends TabularModelServiceError
    case object ModelNotActive extends TabularModelServiceError
    case object CantExportTabularModel extends TabularModelServiceError
  }

}
