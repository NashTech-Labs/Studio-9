package baile.services.cv.prediction

import java.time.Instant
import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.cv.prediction.CVPredictionDao
import baile.daocommons.WithId
import baile.daocommons.sorting.Field
import baile.domain.asset.AssetType
import baile.domain.cv.model.{ CVModel, CVModelStatus, CVModelType }
import baile.domain.cv.prediction.{ CVPrediction, CVPredictionStatus }
import baile.domain.images._
import baile.domain.usermanagement.User
import baile.services.asset.AssetService
import baile.services.asset.AssetService.{
  WithOwnershipTransfer,
  AssetCreateErrors,
  WithCreate,
  WithNestedUsageTracking,
  WithProcess,
  WithSharedAccess
}
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.EntityService.WithSortByField
import baile.services.cortex.job.CortexJobService
import baile.services.cortex.job.SupportedCortexJobTypes._
import baile.services.cv.CVTLModelPrimitiveService
import baile.services.cv.model.{ CVModelCommonService, CVModelService }
import baile.services.cv.prediction.CVPredictionService.CVPredictionCreateError.UnsupportedAlbumLabelMode
import baile.services.cv.prediction.CVPredictionService.{ CVPredictionCreateError, CVPredictionServiceError }
import baile.services.dcproject.DCProjectPackageService
import baile.domain.cv.prediction.CVModelPredictOptions
import baile.services.images.{ AlbumService, ImagesCommonService }
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.services.remotestorage.RemoteStorageService
import baile.services.table.TableService
import baile.utils.validation.Option._
import cats.data.EitherT
import cats.implicits._
import cortex.api.job.album.common.Image
import cortex.api.job.common.ClassReference
import cortex.api.job.table.TableMeta
import cortex.api.job.computervision.{ CVModelType => CortexCVModelType, _ }

import scala.concurrent.{ ExecutionContext, Future }

class CVPredictionService(
  albumService: AlbumService,
  cvModelService: CVModelService,
  cvModelCommonService: CVModelCommonService,
  tableService: TableService,
  cvModelPrimitiveService: CVTLModelPrimitiveService,
  imagesCommonService: ImagesCommonService,
  pictureStorage: RemoteStorageService,
  packageService: DCProjectPackageService,
  protected val dao: CVPredictionDao,
  protected val cortexJobService: CortexJobService,
  protected val processService: ProcessService,
  protected val assetSharingService: AssetSharingService,
  protected val projectService: ProjectService
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends AssetService[CVPrediction, CVPredictionServiceError]
  with WithSortByField[CVPrediction, CVPredictionServiceError]
  with WithProcess[CVPrediction, CVPredictionServiceError]
  with WithSharedAccess[CVPrediction, CVPredictionServiceError]
  with WithNestedUsageTracking[CVPrediction, CVPredictionServiceError]
  with WithOwnershipTransfer[CVPrediction]
  with WithCreate[CVPrediction, CVPredictionServiceError, CVPredictionCreateError] {

  import CVPredictionService._

  override val assetType: AssetType = AssetType.CvPrediction
  override val notFoundError: CVPredictionServiceError = CVPredictionServiceError.NotFound
  override val forbiddenError: CVPredictionServiceError = CVPredictionServiceError.AccessDenied
  override val sortingFieldNotFoundError: CVPredictionServiceError =
    CVPredictionServiceError.SortingFieldUnknown
  override val inUseError: CVPredictionServiceError = CVPredictionServiceError.CVPredictionInUse

  override protected val createErrors: AssetCreateErrors[CVPredictionCreateError] = CVPredictionCreateError
  override protected val findField: String => Option[Field] = Map(
    "name" -> CVPredictionDao.Name,
    "created" -> CVPredictionDao.Created,
    "updated" -> CVPredictionDao.Updated
  ).get

  override def updateOwnerId(cvPrediction: CVPrediction, ownerId: UUID): CVPrediction = {
    cvPrediction.copy(ownerId = ownerId)
  }

  // scalastyle:off method.length
  def create(
    modelId: String,
    inputAlbumId: String,
    name: Option[String],
    description: Option[String],
    outputAlbumName: Option[String],
    cvModelPredictOptions: Option[CVModelPredictOptions],
    evaluate: Boolean
  )(implicit user: User): Future[Either[CVPredictionCreateError, WithId[CVPrediction]]] = {

    def validateAlbumLabelMode(
      albumLabelMode: AlbumLabelMode,
      modelType: CVModelType
    ): Either[CVPredictionCreateError, Unit] =
      if (evaluate) {
        cvModelPrimitiveService.validateAlbumAndModelCompatibility(albumLabelMode, modelType, UnsupportedAlbumLabelMode)
      } else {
        ().asRight
      }

    def startMonitoring(
      jobId: UUID,
      cvPredictionId: String,
      outputAlbumId: String
    ): Future[Unit] = {

      processService.startProcess(
        jobId,
        cvPredictionId,
        AssetType.CvPrediction,
        classOf[CVPredictionResultHandler],
        CVPredictionResultHandler.Meta(cvPredictionId, evaluate, user.id),
        user.id
      ).map(_ => ())
    }

    def ensureCVModelActive(cvModel: WithId[CVModel]): Either[CVPredictionCreateError, Unit] = {
      if (cvModel.entity.status == CVModelStatus.Active) ().asRight
      else CVPredictionCreateError.CVModelNotActive.asLeft
    }


    def loadCortexCVModelType(cvModelType: CVModelType): Future[CortexCVModelType] = {
      cvModelType match {
        case tl: CVModelType.TL =>
          for {
            modelTypeOperator <- cvModelPrimitiveService.loadTLConsumerPrimitive(
              tl.consumer
            )
            modelPackage <- packageService.loadPackageMandatory(modelTypeOperator.entity.packageId)
            featureExtractorArchitectureOperator <- cvModelPrimitiveService.loadFeatureExtractorArchitecturePrimitive(
              tl.featureExtractorArchitecture
            )
            featureExtractorArchitecturePackage <- packageService.loadPackageMandatory(
              featureExtractorArchitectureOperator.entity.packageId
            )
          } yield {
            cvModelCommonService.buildCortexTLModel(
              tl,
              cvModelCommonService.buildClassReference(modelTypeOperator.entity, modelPackage.entity),
              Some(cvModelCommonService.buildClassReference(
                featureExtractorArchitectureOperator.entity,
                featureExtractorArchitecturePackage.entity
              ))
            )
          }

        case CVModelType.Custom(classReference, _) =>
          for {
            modelPackage <- packageService.loadPackageMandatory(classReference.packageId)
          } yield {
            cvModelCommonService.buildCortexCustomModel(
              ClassReference(
                modelPackage.entity.location,
                classReference.className,
                classReference.moduleName
              )
            )
          }
      }
    }

    val result = for {
      createParams <- validateAndGetAssetCreateParams(name, None)
      cvModel <- EitherT(cvModelService.get(modelId)).leftMap(_ => CVPredictionCreateError.CVModelNotFound)
      _ <- EitherT.fromEither[Future](ensureCVModelActive(cvModel))
      inputAlbum <- EitherT(albumService.get(inputAlbumId)).leftMap(_ => CVPredictionCreateError.AlbumNotFound)
      _ <- EitherT.fromEither[Future](validateAlbumLabelMode(inputAlbum.entity.labelMode, cvModel.entity.`type`))
      cortexModelReference <- EitherT.fromOption[Future](
        cvModel.entity.cortexModelReference,
        CVPredictionCreateError.CVModelCantBeUsed
      )
      pictures <- EitherT.right[CVPredictionCreateError](imagesCommonService.getPictures(
        albumId = inputAlbumId,
        onlyTagged = evaluate && CVModelCommonService.isClassifier(cvModel.entity.`type`)
      ))
      _ <- EitherT.cond[Future](
        pictures.nonEmpty,
        (),
        CVPredictionCreateError.NoPicturesInAlbum
      )
      _ <- EitherT.cond[Future](
        cvModelPredictOptions.forall(_.loi.forall(_.nonEmpty)),
        (),
        CVPredictionCreateError.EmptyLabelsOfInterest
      )
      albumType = if (evaluate) AlbumType.TrainResults else AlbumType.Derived
      outputAlbum <- EitherT.right[CVPredictionCreateError](cvModelCommonService.createOutputAlbum(
        picturesPrefix = inputAlbum.entity.picturesPrefix,
        namePrefix = outputAlbumName.getOrElse(s"${ createParams.name } OUT"),
        labelMode = cvModel.entity.`type` match {
          case CVModelType.TL(consumer, _) => consumer match {
            case _: CVModelType.TLConsumer.Classifier => AlbumLabelMode.Classification
            case _: CVModelType.TLConsumer.Localizer => AlbumLabelMode.Localization
            case _: CVModelType.TLConsumer.Decoder => AlbumLabelMode.Classification
          }
          case CVModelType.Custom(_, labelMode) => labelMode.getOrElse(AlbumLabelMode.Classification)
        },
        albumType = albumType,
        inputVideo = cvModel.entity.`type` match {
          // we support video output for localization
          case CVModelType.TL(_: CVModelType.TLConsumer.Localizer, _) if !evaluate => inputAlbum.entity.video
          case _ => None
        },
        userId = user.id
      ))
      probabilityPredictionTable <- EitherT.right[CVPredictionCreateError] {
        cvModel.entity.`type` match {
          case CVModelType.TL(_: CVModelType.TLConsumer.Decoder, _) => Future.successful(None)
          case _ => cvModelCommonService.createPredictionTable(
            baseTableName = createParams.name
          ).map(Some(_))
        }
      }
      modelType <- EitherT.right[CVPredictionCreateError](loadCortexCVModelType(cvModel.entity.`type`))
      jobId <- EitherT.right[CVPredictionCreateError](launchPrediction(
        modelType,
        cortexModelReference.cortexId,
        inputAlbum.entity,
        outputAlbum.entity,
        pictures,
        cvModelPredictOptions,
        probabilityPredictionTableMeta = probabilityPredictionTable.map { table =>
          tableService.buildTableMeta(table.entity)
        },
        evaluate
      ))
      cvPrediction <- EitherT.rightT[Future, CVPredictionCreateError](createCVPrediction(
        name = createParams.name,
        modelId = modelId,
        inputAlbumId = inputAlbumId,
        outputAlbumId = outputAlbum.id,
        probabilityPredictionTableId = probabilityPredictionTable.map(_.id),
        description = description,
        cvModelPredictOptions = cvModelPredictOptions
      ))
      cvPredictionId <- EitherT.right[CVPredictionCreateError](dao.create(cvPrediction))
      _ <- EitherT.right[CVPredictionCreateError](
        startMonitoring(jobId, cvPredictionId, outputAlbum.id)
      )
    } yield WithId(cvPrediction, cvPredictionId)
    result.value
  }
  // scalastyle:on method.length

  def update(
    id: String,
    newName: Option[String],
    newDescription: Option[String]
  )(implicit user: User): Future[Either[CVPredictionServiceError, WithId[CVPrediction]]] = {

    update(
      id,
      _ => newName.validate(name => validateAssetName(
        name,
        Option(id),
        CVPredictionServiceError.PredictionNameCanNotBeEmpty,
        CVPredictionServiceError.PredictionAlreadyExists
      )),
      prediction => prediction.copy(
        name = newName.getOrElse(prediction.name),
        description = newDescription orElse prediction.description
      )
    )
  }

  // scalastyle:off parameter.number
  private def launchPrediction(
    modelType: CortexCVModelType,
    cortexModelId: String,
    inputAlbum: Album,
    outputAlbum: Album,
    inputPictures: Seq[WithId[Picture]],
    cvModelPredictOptions: Option[CVModelPredictOptions],
    probabilityPredictionTableMeta: Option[TableMeta],
    evaluate: Boolean
  )(implicit user: User): Future[UUID] = {

    def buildPredictionRequest(cortexId: String): PredictRequest = {
      val videoParams = inputAlbum.labelMode match {
        case AlbumLabelMode.Classification =>
          None
        case AlbumLabelMode.Localization =>
          outputAlbum.video.map { outputVideo =>
            VideoParams(
              targetVideoFilePath = pictureStorage.path(
                imagesCommonService.getImagesPathPrefix(outputAlbum),
                outputVideo.filePath
              ),
              videoAssembleFrameRate = outputVideo.frameRate,
              videoAssembleHeight = outputVideo.height,
              videoAssembleWidth = outputVideo.width
            )
          }
      }
      val targetPrefix = modelType.`type` match {
        case CortexCVModelType.Type.TlModel(TLModel(Some(value), _)) if value.`type`.isAutoencoderType =>
          Some(imagesCommonService.getImagesPathPrefix(outputAlbum))
        case _ =>
          None
      }
      val predictRequest = PredictRequest(
        modelType = Some(modelType),
        modelId = cortexId,
        images = inputPictures.map { picture =>
          Image(
            filePath = picture.entity.filePath,
            referenceId = Some(picture.id),
            fileSize = picture.entity.fileSize,
            displayName = Some(picture.entity.fileName)
          )
        },
        filePathPrefix = imagesCommonService.getImagesPathPrefix(inputAlbum),
        probabilityPredictionTable = probabilityPredictionTableMeta,
        videoParams = videoParams,
        targetPrefix = targetPrefix
      )
      cvModelPredictOptions match {
        case Some(options) =>
          predictRequest.copy(
            labelsOfInterest = CVModelCommonService.labelsOfInterestToCortexLabelsOfInterest(options.loi),
            defaultVisualThreshold = options.defaultVisualThreshold.map(_.toDouble)
          )
        case None =>
          predictRequest
      }
    }

    def buildEvaluateRequest(cortexId: String): EvaluateRequest = {
      val baseRequest = EvaluateRequest(
        modelType = Some(modelType),
        modelId = cortexId,
        images = imagesCommonService.convertPicturesToCortexTaggedImages(inputPictures),
        filePathPrefix = imagesCommonService.getImagesPathPrefix(inputAlbum),
        probabilityPredictionTable = probabilityPredictionTableMeta
      )
      cvModelPredictOptions match {
        case Some(options) =>
          baseRequest.copy(
            labelsOfInterest = CVModelCommonService.labelsOfInterestToCortexLabelsOfInterest(options.loi),
            defaultVisualThreshold = options.defaultVisualThreshold.map(_.toDouble)
          )
        case None =>
          baseRequest
      }
    }

    if (!evaluate) cortexJobService.submitJob(
      buildPredictionRequest(cortexModelId),
      user.id
    )
    else cortexJobService.submitJob(buildEvaluateRequest(cortexModelId), user.id)

  }

  // scalastyle:on parameter.number

  private def createCVPrediction(
    name: String,
    modelId: String,
    inputAlbumId: String,
    outputAlbumId: String,
    probabilityPredictionTableId: Option[String],
    description: Option[String],
    cvModelPredictOptions: Option[CVModelPredictOptions]
  )(implicit user: User): CVPrediction = {
    val dateTime = Instant.now

    CVPrediction(
      ownerId = user.id,
      name = name,
      status = CVPredictionStatus.Running,
      created = dateTime,
      updated = dateTime,
      modelId = modelId,
      inputAlbumId = inputAlbumId,
      outputAlbumId = outputAlbumId,
      evaluationSummary = None,
      predictionTimeSpentSummary = None,
      evaluateTimeSpentSummary = None,
      description = description,
      probabilityPredictionTableId = probabilityPredictionTableId,
      cvModelPredictOptions = cvModelPredictOptions
    )
  }

  override protected def preDelete(
    asset: WithId[CVPrediction]
  )(implicit user: User): Future[Either[CVPredictionServiceError, Unit]] = {

    def dropOutputAlbum(): Future[Either[CVPredictionServiceError, Unit]] = {
      val albumId = asset.entity.outputAlbumId

      def loadOutputAlbum(): Future[WithId[Album]] = albumService.get(albumId).map(_.valueOr {
        error => throw new RuntimeException(s"Unexpected error received while getting album $albumId : $error")
      })

      def deleteIfNotInLibrary(album: WithId[Album]): Future[Unit] =
        if (album.entity.inLibrary) Future.successful(())
        else albumService.deleteAlbum(album.id).map(_ => ())

      for {
        album <- loadOutputAlbum()
        result <- deleteIfNotInLibrary(album)
      } yield result.asRight
    }

    val result = for {
      _ <- EitherT(super.preDelete(asset))
      _ <- EitherT(dropOutputAlbum())
    } yield ()

    result.value
  }

}

object CVPredictionService {

  sealed trait CVPredictionCreateError

  object CVPredictionCreateError extends AssetCreateErrors[CVPredictionCreateError] {

    case object PredictionAlreadyExists extends CVPredictionCreateError
    case object PredictionNameCanNotBeEmpty extends CVPredictionCreateError
    case object NameNotSpecified extends CVPredictionCreateError
    case object CVModelNotFound extends CVPredictionCreateError
    case object CVModelNotActive extends CVPredictionCreateError
    case object CVModelCantBeUsed extends CVPredictionCreateError
    case object AlbumNotFound extends CVPredictionCreateError
    case object NoPicturesInAlbum extends CVPredictionCreateError
    case object EmptyLabelsOfInterest extends CVPredictionCreateError
    case object UnsupportedAlbumLabelMode extends CVPredictionCreateError

    override val nameNotSpecifiedError: CVPredictionCreateError = NameNotSpecified
    override val emptyNameError: CVPredictionCreateError = PredictionNameCanNotBeEmpty

    override def nameAlreadyExistsError(name: String): CVPredictionCreateError = PredictionAlreadyExists
  }

  sealed trait CVPredictionServiceError

  object CVPredictionServiceError {

    case object NotFound extends CVPredictionServiceError
    case object SortingFieldUnknown extends CVPredictionServiceError
    case object AccessDenied extends CVPredictionServiceError
    case object AlbumNotFound extends CVPredictionServiceError
    case object PredictionAlreadyExists extends CVPredictionServiceError
    case object PredictionNameCanNotBeEmpty extends CVPredictionServiceError
    case object CVPredictionInUse extends CVPredictionServiceError

  }

  case class InputPictures(pictures: Seq[WithId[Picture]], filePathPrefix: String)

}
