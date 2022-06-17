package baile.services.cv.model

import java.time.Instant
import java.util.UUID

import akka.event.LoggingAdapter
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.dao.cv.model.CVModelDao
import baile.dao.cv.model.tlprimitives.CVTLModelPrimitiveDao
import baile.dao.cv.model.tlprimitives.CVTLModelPrimitiveDao.{ CVTLModelPrimitiveTypeIs, ClassNameIs, ModuleNameIs }
import baile.daocommons.WithId
import baile.daocommons.sorting.Field
import baile.domain.asset.AssetType
import baile.domain.common.{ ClassReference, Version }
import baile.domain.cv.model.tlprimitives.{ CVTLModelPrimitive, CVTLModelPrimitiveType }
import baile.domain.cv.model.{ CVModelType, _ }
import baile.domain.dcproject.DCProjectPackage
import baile.domain.images.{ AlbumLabelMode, _ }
import baile.domain.usermanagement.User
import baile.services.asset.AssetService
import baile.services.asset.AssetService.{
  AssetCreateErrors,
  WithCreate,
  WithNestedUsageTracking,
  WithOwnershipTransfer,
  WithProcess,
  WithSharedAccess
}
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.EntityService.WithSortByField
import baile.services.common.MLEntityExportImportService
import baile.services.common.MLEntityExportImportService.{ EntityFileSavedResult, EntityImportError }
import baile.services.cortex.job.CortexJobService
import baile.services.cortex.job.SupportedCortexJobTypes._
import baile.services.cv.CVTLModelPrimitiveService
import baile.services.cv.CVTLModelPrimitiveService.CVTLModelPrimitiveReference
import baile.services.cv.model.CVModelService._
import baile.services.cv.model.util.export.CVModelExportMeta
import baile.services.dcproject.DCProjectPackageService
import baile.services.images.ImagesCommonService
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.services.remotestorage.RemoteStorageService
import baile.utils.validation.Option._
import cats.data.EitherT
import cats.implicits._
import cortex.api.job.computervision
import cortex.api.job.computervision.CVModelImportRequest
import cortex.api.job.common.{ ClassReference => CortexClassReference }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class CVModelService(
  protected val dao: CVModelDao,
  protected val cvModelTLPrimitiveDao: CVTLModelPrimitiveDao,
  protected val cortexJobService: CortexJobService,
  protected val cvModelCommonService: CVModelCommonService,
  protected val imagesCommonService: ImagesCommonService,
  protected val processService: ProcessService,
  protected val assetSharingService: AssetSharingService,
  protected val exportImportService: MLEntityExportImportService,
  protected val projectService: ProjectService,
  protected val cvModelPrimitiveService: CVTLModelPrimitiveService,
  protected val packageService: DCProjectPackageService,
  protected val mlEntitiesStorage: RemoteStorageService
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends AssetService[CVModel, CVModelServiceError]
  with WithSortByField[CVModel, CVModelServiceError]
  with WithProcess[CVModel, CVModelServiceError]
  with WithSharedAccess[CVModel, CVModelServiceError]
  with WithNestedUsageTracking[CVModel, CVModelServiceError]
  with WithCreate[CVModel, CVModelServiceError, CVModelImportError]
  with WithOwnershipTransfer[CVModel]
{

  override val assetType: AssetType = AssetType.CvModel

  override val notFoundError: CVModelServiceError = CVModelServiceError.ModelNotFound
  override val forbiddenError: CVModelServiceError = CVModelServiceError.AccessDenied
  override val sortingFieldNotFoundError: CVModelServiceError = CVModelServiceError.SortingFieldUnknown
  override val inUseError: CVModelServiceError = CVModelServiceError.CVModelInUse
  override val createErrors: AssetCreateErrors[CVModelImportError] = CVModelImportError

  override protected val findField: String => Option[Field] = Map(
    "name" -> CVModelDao.Name,
    "created" -> CVModelDao.Created,
    "updated" -> CVModelDao.Updated
  ).get

  override def updateOwnerId(cvModel: CVModel, ownerId: UUID): CVModel = cvModel.copy(ownerId = ownerId)

  def save(
    id: String,
    name: String,
    description: Option[String]
  )(implicit user: User): Future[Either[CVModelServiceError, WithId[CVModel]]] = {

    def validate(model: WithId[CVModel]): Future[Either[CVModelServiceError, Unit]] = {
      val result = for {
        _ <- EitherT.cond[Future](
          model.entity.status == CVModelStatus.Active,
          (),
          CVModelServiceError.ModelNotActive
        )
        _ <- EitherT.cond[Future](
          !model.entity.inLibrary,
          (),
          CVModelServiceError.ModelAlreadyInLibrary
        )
        _ <- EitherT(validateAssetName[CVModelServiceError](
          name,
          Option(id),
          CVModelServiceError.EmptyModelName,
          CVModelServiceError.NameIsTaken
        ))
      } yield ()

      result.value
    }

    update(
      id,
      validate,
      model => model.copy(
        name = name,
        description = description orElse model.description,
        inLibrary = true
      )
    )
  }

  // scalastyle:off method.length
  def importModel(
    modelFileSource: Source[ByteString, Any],
    paramsF: Future[Map[String, String]]
  )(implicit user: User, materializer: Materializer): Future[Either[CVModelImportError, WithId[CVModel]]] = {

    import baile.services.cv.model.CVModelService.CVModelImportError._

    def saveModel(
      name: String,
      meta: CVModelExportMeta,
      inLibrary: Boolean,
      modelType: CVModelType
    ): Future[WithId[CVModel]] = {
      val now = Instant.now()
      val model = CVModel(
        ownerId = user.id,
        name = name,
        created = now,
        updated = now,
        status = CVModelStatus.Saving,
        cortexFeatureExtractorReference = None,
        cortexModelReference = None,
        `type` = modelType,
        classNames = meta.classNames,
        featureExtractorId = None,
        description = meta.description,
        inLibrary = inLibrary,
        experimentId = None
      )

      dao.create(model).map(WithId(model, _))
    }

    def translateImportError(error: EntityImportError[CVModelImportError]): CVModelImportError = error match {
      case EntityImportError.MetaIsTooBig => ImportedMetaIsTooBig
      case EntityImportError.InvalidMetaFormat(error) => ImportedMetaFormatError(error)
      case EntityImportError.ImportHandlingFailed(e) => e
    }

    def handleSavedEntityFile(
      entityFileSaveResult: EntityFileSavedResult[CVModelExportMeta]
    ): Future[Either[CVModelImportError, WithId[CVModel]]] = {
      val result = for {
        params <- EitherT.right[CVModelImportError](paramsF)
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
        modelTypeTuple <- EitherT(
          entityFileSaveResult.meta.modelType match {
            case tl: CVModelExportMeta.CVModelType.TL => exportMetaToTLModelType(tl)
            case custom: CVModelExportMeta.CVModelType.Custom => exportMetaToCustomModelType(custom)
          }
        )
        (modelType, cortexModelType) = modelTypeTuple
        featureExtractorOnly = entityFileSaveResult.meta.modelType match {
          case CVModelExportMeta.CVModelType.TL(_, _, feOnly) => feOnly
          case _ => false
        }
        importJobId <- EitherT.right[CVModelImportError](cortexJobService.submitJob(
          CVModelImportRequest(
            path = entityFileSaveResult.filePath,
            modelType = Some(cortexModelType),
            feOnly = featureExtractorOnly
          ),
          user.id
        ))
        model <- EitherT.right[CVModelImportError](saveModel(
          createParams.name,
          entityFileSaveResult.meta,
          createParams.inLibrary,
          modelType
        ))
        _ <- EitherT.right[CVModelImportError](processService.startProcess(
          jobId = importJobId,
          targetId = model.id,
          targetType = AssetType.CvModel,
          handlerClass = classOf[CVModelImportResultHandler],
          meta = CVModelImportResultHandler.Meta(model.id, entityFileSaveResult.filePath),
          userId = user.id
        ))
      } yield model

      result.value
    }

    exportImportService.importEntity[CVModelImportError, WithId[CVModel], CVModelExportMeta](
      modelFileSource,
      _ => EitherT.rightT[Future, CVModelImportError](()),
      handleSavedEntityFile
    ).map(_.leftMap(translateImportError))
  }
  // scalastyle:on method.length

  def update(
    id: String,
    newName: Option[String],
    newDescription: Option[String]
  )(implicit user: User): Future[Either[CVModelServiceError, WithId[CVModel]]] = {

    update(
      id,
      _ => newName.validate(name => validateAssetName(
        name,
        Option(id),
        CVModelServiceError.EmptyModelName,
        CVModelServiceError.NameIsTaken
      )),
      model => model.copy(
        name = newName.getOrElse(model.name),
        description = newDescription orElse model.description
      )
    )
  }

  def export(
    id: String
  )(implicit user: User): Future[Either[CVModelServiceError, Source[ByteString, Any]]] = {
    val result = for {
      model <- EitherT(get(id))
      _ <- EitherT.cond[Future](
        model.entity.status == CVModelStatus.Active,
        (),
        CVModelServiceError.ModelNotActive
      )
      modelReference <- EitherT.fromOption[Future](
        model.entity.cortexModelReference,
        CVModelServiceError.CantExportCVModel: CVModelServiceError
      )
      modelType <- EitherT.right[CVModelServiceError](
        model.entity.`type` match {
          case tl: CVModelType.TL => tlModelTypeToExportMeta(tl)
          case custom: CVModelType.Custom => customModelToExportMeta(custom)
        }
      )
      source <- EitherT.right[CVModelServiceError](
        exportImportService.exportEntity(
          modelReference.cortexFilePath,
          CVModelExportMeta(model.entity, modelType)
        )
      )
    } yield source

    result.value
  }

  def getStateFileUrl(
    id: String
  )(implicit user: User): Future[Either[CVModelServiceError, String]] = {
    val result = for {
      model <- EitherT(get(id))
      _ <- EitherT.cond[Future](
        model.entity.status == CVModelStatus.Active,
        (),
        CVModelServiceError.ModelNotActive
      )
      modelReference <- EitherT.fromOption[Future](
        model.entity.cortexModelReference,
        CVModelServiceError.ModelFilePathNotFound: CVModelServiceError
      )
    } yield mlEntitiesStorage.getExternalUrl(modelReference.cortexFilePath)

    result.value
  }

  private def generateTLConsumer(operator: WithId[CVTLModelPrimitive]): CVModelType.TLConsumer = {
    operator.entity.cvTLModelPrimitiveType match {
      case CVTLModelPrimitiveType.Decoder => CVModelType.TLConsumer.Decoder(operator.id)
      case CVTLModelPrimitiveType.Classifier => CVModelType.TLConsumer.Classifier(operator.id)
      case CVTLModelPrimitiveType.Detector => CVModelType.TLConsumer.Localizer(operator.id)
      case CVTLModelPrimitiveType.UTLP => throw new RuntimeException("Invalid operator type for cv model")
    }
  }

  private def getModelOperatorType(consumer: CVModelExportMeta.CVModelType.TLConsumer): CVTLModelPrimitiveType = {
    consumer match {
      case _: CVModelExportMeta.CVModelType.TLConsumer.Classifier => CVTLModelPrimitiveType.Classifier
      case _: CVModelExportMeta.CVModelType.TLConsumer.Localizer => CVTLModelPrimitiveType.Detector
      case _: CVModelExportMeta.CVModelType.TLConsumer.Decoder => CVTLModelPrimitiveType.Decoder
    }
  }


  private def exportMetaToTLModelType(
    tl: CVModelExportMeta.CVModelType.TL
  )(implicit user: User) = {
    val result = for {
      modelOperatorReference <- EitherT(
        loadCVTLModelPrimitiveReference(
          tl.consumer.classReference,
          getModelOperatorType(tl.consumer)
        )
      )
      architectureOperatorReference <- EitherT(
        loadCVTLModelPrimitiveReference(
          tl.featureExtractorReference,
          CVTLModelPrimitiveType.UTLP
        )
      )
      consumer = generateTLConsumer(modelOperatorReference.operator)
      modelType = CVModelType.TL(
        consumer = consumer,
        featureExtractorArchitecture = architectureOperatorReference.operator.id
      )
      cortexModelType = cvModelCommonService.buildCortexTLModel(
        cvModelType = modelType,
        classReference = CortexClassReference(
          packageLocation = modelOperatorReference.packageLocation,
          className = modelOperatorReference.operator.entity.className,
          moduleName = modelOperatorReference.operator.entity.moduleName
        ),
        featureExtractorClassReference = Some(CortexClassReference(
          packageLocation = architectureOperatorReference.packageLocation,
          className = architectureOperatorReference.operator.entity.className,
          moduleName = architectureOperatorReference.operator.entity.moduleName
        ))
      )
    } yield (modelType, cortexModelType)

    result.value
  }

  private def exportMetaToCustomModelType(
    custom: CVModelExportMeta.CVModelType.Custom
  )(implicit user: User): Future[Either[CVModelImportError, (CVModelType.Custom, computervision.CVModelType)]] = {
    val result = for {
      packageInfo <- loadPackageInfo(custom.classReference)
      modelType = CVModelType.Custom(
        classReference = ClassReference(
          packageId = packageInfo.id,
          moduleName = custom.classReference.moduleName,
          className = custom.classReference.className
        ),
        labelMode = custom.labelMode.map(exportMetaToAlbumLabelMode)
      )
      cortexModelType = cvModelCommonService.buildCortexCustomModel(
        CortexClassReference(
          packageLocation = packageInfo.entity.location,
          className = custom.classReference.className,
          moduleName = custom.classReference.moduleName
        )
      )
    } yield (modelType, cortexModelType)

    result.value
  }

  private def tlModelTypeToExportMeta(modelType: CVModelType.TL): Future[CVModelExportMeta.CVModelType.TL] = {
    for {
      architectureOperator <- cvModelPrimitiveService.loadFeatureExtractorArchitecturePrimitive(
        modelType.featureExtractorArchitecture
      )
      architecturePackage <- packageService.loadPackageMandatory(architectureOperator.entity.packageId)
      architectureClassReference = CVModelExportMeta.ClassReference(
        moduleName = architectureOperator.entity.moduleName,
        className = architectureOperator.entity.className,
        packageName = architecturePackage.entity.name,
        packageVersion = architecturePackage.entity.version.map(versionToExportMeta)
      )

      modelOperator <- cvModelPrimitiveService.loadTLConsumerPrimitive(modelType.consumer)
      modelPackage <- packageService.loadPackageMandatory(modelOperator.entity.packageId)
      modelOperatorClassReference = CVModelExportMeta.ClassReference(
        moduleName = modelOperator.entity.moduleName,
        className = modelOperator.entity.className,
        packageName = modelPackage.entity.name,
        packageVersion = modelPackage.entity.version.map(versionToExportMeta)
      )
      consumer = modelType.consumer match {
        case _: CVModelType.TLConsumer.Classifier =>
          CVModelExportMeta.CVModelType.TLConsumer.Classifier(
            classReference = modelOperatorClassReference
          )
        case _: CVModelType.TLConsumer.Localizer =>
          CVModelExportMeta.CVModelType.TLConsumer.Localizer(
            classReference = modelOperatorClassReference
          )
        case _: CVModelType.TLConsumer.Decoder =>
          CVModelExportMeta.CVModelType.TLConsumer.Decoder(
            classReference = modelOperatorClassReference
          )
      }
    } yield {
      CVModelExportMeta.CVModelType.TL(
        consumer = consumer,
        featureExtractorReference = architectureClassReference
      )
    }
  }

  private def customModelToExportMeta(modelType: CVModelType.Custom): Future[CVModelExportMeta.CVModelType.Custom] = {
    for {
      packageInfo <- packageService.loadPackageMandatory(modelType.classReference.packageId)
    } yield {
      CVModelExportMeta.CVModelType.Custom(
        classReference = CVModelExportMeta.ClassReference(
          className = modelType.classReference.className,
          moduleName = modelType.classReference.moduleName,
          packageName = packageInfo.entity.name,
          packageVersion = packageInfo.entity.version.map(versionToExportMeta)
        ),
        labelMode = modelType.labelMode.map(albumLabelModeToExportMeta)
      )
    }
  }

  private def loadPackageInfo(
    classReference: CVModelExportMeta.ClassReference
  )(implicit user: User): EitherT[Future, CVModelImportError, WithId[DCProjectPackage]] = {
    val version = classReference.packageVersion.map(exportMetaToVersion)
    EitherT.fromOptionF(
      packageService.getPackageByNameAndVersion(
        classReference.packageName,
        version
      )(user),
      CVModelImportError.PackageNotFound(classReference.packageName, version)
    )
  }

  private def loadCVTLModelPrimitiveReference(
    classReference: CVModelExportMeta.ClassReference,
    operatorType: CVTLModelPrimitiveType
  )(implicit user: User): Future[Either[CVModelImportError, CVTLModelPrimitiveReference]] = {
    val result = for {
      operators <- EitherT.right[CVModelImportError](cvModelTLPrimitiveDao.listAll(
        ModuleNameIs(classReference.moduleName) &&
        ClassNameIs(classReference.className) &&
        CVTLModelPrimitiveTypeIs(operatorType)
      ))
      packageInfo <- loadPackageInfo(classReference)
      operator <- EitherT.fromOption[Future].apply[CVModelImportError, WithId[CVTLModelPrimitive]](
        operators.find(_.entity.packageId == packageInfo.id),
        CVModelImportError.OperatorNotFound(classReference.moduleName, classReference.className)
      )
    } yield {
      CVTLModelPrimitiveReference(operator, packageInfo.entity.location)
    }

    result.value
  }

  private def albumLabelModeToExportMeta(labelMode: AlbumLabelMode): CVModelExportMeta.AlbumLabelMode =
    labelMode match {
      case AlbumLabelMode.Classification => CVModelExportMeta.AlbumLabelMode.Classification
      case AlbumLabelMode.Localization => CVModelExportMeta.AlbumLabelMode.Localization
    }

  private def exportMetaToAlbumLabelMode(labelMode: CVModelExportMeta.AlbumLabelMode): AlbumLabelMode =
    labelMode match {
      case CVModelExportMeta.AlbumLabelMode.Classification => AlbumLabelMode.Classification
      case CVModelExportMeta.AlbumLabelMode.Localization => AlbumLabelMode.Localization
    }

  private def versionToExportMeta(version: Version): CVModelExportMeta.Version =
    CVModelExportMeta.Version(version.major, version.minor, version.patch, version.suffix)

  private def exportMetaToVersion(version: CVModelExportMeta.Version): Version =
    Version(version.major, version.minor, version.patch, version.suffix)

}

object CVModelService {

  sealed trait CVModelImportError
  object CVModelImportError extends AssetCreateErrors[CVModelImportError] {
    case object InLibraryWrongFormat extends CVModelImportError
    case object EmptyModelName extends CVModelImportError
    case object NameIsTaken extends CVModelImportError
    case object NameNotSpecified extends CVModelImportError
    case object ImportedMetaIsTooBig extends CVModelImportError
    case class ImportedMetaFormatError(error: String) extends CVModelImportError
    case object LocalizationModeNotSpecified extends CVModelImportError
    case object InvalidCVModelType extends CVModelImportError
    case class UnsupportedLocalizationMode(mode: CVModelLocalizationMode) extends CVModelImportError
    case class PackageNotFound(packageName: String, version: Option[Version]) extends CVModelImportError
    case class OperatorNotFound(moduleName: String, className: String) extends CVModelImportError

    override val nameNotSpecifiedError: CVModelImportError = NameNotSpecified
    override val emptyNameError: CVModelImportError = EmptyModelName

    override def nameAlreadyExistsError(name: String): CVModelImportError = NameIsTaken
  }

  sealed trait CVModelServiceError
  object CVModelServiceError {
    case object ModelNotFound extends CVModelServiceError
    case object AccessDenied extends CVModelServiceError
    case object SortingFieldUnknown extends CVModelServiceError
    case object CantDeleteRunningModel extends CVModelServiceError
    case object ModelNotActive extends CVModelServiceError
    case object EmptyModelName extends CVModelServiceError
    case object NameIsTaken extends CVModelServiceError
    case object CVModelInUse extends CVModelServiceError
    case object CantExportCVModel extends CVModelServiceError
    case object ModelAlreadyInLibrary extends CVModelServiceError
    case object ModelFilePathNotFound extends CVModelServiceError
  }

  case class OperatorReference(
    operatorId: String,
    moduleName: String,
    className: String,
    packageLocation: Option[String]
  )

  private[cv] case class BaseCVModelCreateParams(
    inputAlbum: WithId[Album],
    testInputAlbum: Option[WithId[Album]],
    architectureOperatorReference: OperatorReference,
    modelOperatorReference: OperatorReference
  )

}
