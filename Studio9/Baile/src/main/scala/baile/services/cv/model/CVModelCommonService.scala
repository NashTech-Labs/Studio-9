package baile.services.cv.model

import java.time.Instant
import java.util.UUID

import baile.dao.asset.Filters.{ NameIs, OwnerIdIs }
import baile.dao.cv.model.CVModelDao
import baile.dao.images.PictureDao.{ AlbumIdIs, FilePathIn }
import baile.dao.images.{ AlbumDao, PictureDao }
import baile.dao.table.TableDao
import baile.daocommons.WithId
import baile.domain.common.{ ConfusionMatrixCell, CortexModelReference }
import baile.domain.cv.model.CVModelType.TLConsumer.{ Classifier, Decoder, Localizer }
import baile.domain.cv.model._
import baile.domain.cv.model.tlprimitives.CVTLModelPrimitive
import baile.domain.cv.AugmentationSummaryCell
import baile.domain.dcproject.DCProjectPackage
import baile.domain.images._
import baile.domain.images.augmentation.MirroringAxisToFlip.{ Both, Horizontal, Vertical }
import baile.domain.images.augmentation.OcclusionMode.{ Background, Zero }
import baile.domain.images.augmentation.TranslationMode.{ Constant, Reflect }
import baile.domain.images.augmentation._
import baile.domain.table._
import baile.domain.usermanagement.User
import baile.services.common.EntityUpdateFailedException
import baile.domain.cv.{ CommonTrainParams, LabelOfInterest }
import baile.services.cv.CVTLModelPrimitiveService
import baile.services.images.ImagesCommonService.AugmentationResultImage
import baile.services.images.{ AlbumAugmentationUtils, AlbumService, ImagesCommonService }
import baile.services.table.TableService
import baile.utils.TryExtensions._
import baile.utils.{ CollectionProcessing, UniqueNameGenerator }
import cats.Id
import cats.data.OptionT
import cats.implicits._
import cortex.api.job.album.augmentation.MirroringAxisToFlip.{ BOTH, HORIZONTAL, VERTICAL }
import cortex.api.job.album.augmentation.OcclusionMode.{ BACKGROUND, ZERO }
import cortex.api.job.album.augmentation.RequestedAugmentation.Params
import cortex.api.job.album.augmentation.TranslationMode.{ CONSTANT, REFLECT }
import cortex.api.job.album.augmentation.{ RequestedAugmentation, AugmentationSummary => CortexAugmentationSummary }
import cortex.api.job.common.{ ConfusionMatrixCell => CortexConfusionMatrixCell }
import cortex.api.job.computervision.{
  CVModelTrainRequest,
  PredictedImage,
  PredictedTag,
  ProbabilityPredictionAreaColumns,
  ProbabilityPredictionTableSchema,
  AutoAugmentationParams => CortexAutoAugmentationParams,
  CVModelType => CortexCVModelType,
  CustomModel => CortexCustomModel,
  InputSize => CortextInputSize,
  LabelOfInterest => CortexLabelOfInterest,
  TLModel => CortexTLModel,
  TLModelType => CortexTLModelType
}
import cortex.api.job.common.ClassReference

import scala.collection.Iterable
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Success, Try }

class CVModelCommonService(
  albumDao: AlbumDao,
  modelDao: CVModelDao,
  pictureDao: PictureDao,
  tableDao: TableDao,
  albumService: AlbumService,
  imagesCommonService: ImagesCommonService,
  tableService: TableService,
  cvModelPrimitiveService: CVTLModelPrimitiveService,
  albumPopulationBatchSize: Int,
  albumPopulationParallelismLevel: Int
)(implicit val ec: ExecutionContext) {

  private[cv] def validatePicturesCount[F](
    albumId: String,
    error: F,
    onlyTagged: Boolean = true
  ): Future[Either[F, Unit]] =
    imagesCommonService.countPictures(albumId, onlyTagged).map { count =>
      if (count > 0) ().asRight else error.asLeft
    }

  private[cv] def createOutputAlbum(
    picturesPrefix: String,
    namePrefix: String,
    labelMode: AlbumLabelMode,
    albumType: AlbumType,
    inputVideo: Option[Video] = None,
    inLibrary: Boolean = false,
    albumStatus: AlbumStatus = AlbumStatus.Saving,
    userId: UUID
  ): Future[WithId[Album]] =
    for {
      name <- UniqueNameGenerator.generateUniqueName[Future](
        prefix = namePrefix,
        suffixDelimiter = " "
      )(name => imagesCommonService.countUserAlbums(name, None, userId).map(_ == 0))
      now = Instant.now
      album <- albumDao.create { id =>
        Album(
          ownerId = userId,
          name = name,
          status = albumStatus,
          `type` = albumType,
          labelMode = labelMode,
          created = now,
          updated = now,
          inLibrary = inLibrary,
          picturesPrefix = picturesPrefix,
          video = inputVideo.map { oldVideo =>
            oldVideo.copy(
              filePath = s"$id.mp4",
              frameRate = (oldVideo.frameRate / oldVideo.frameCaptureRate).max(1),
              frameCaptureRate = 1
            )
          },
          description = None,
          augmentationTimeSpentSummary = None
        )
      }
    } yield album

  private[cv] def buildCortexTLConsumer(
    consumer: CVModelType.TLConsumer,
    classReference: ClassReference
  ): CortexTLModelType = {
    val cortexConsumer = consumer match {
      case _: Classifier => CortexTLModelType.Type.ClassifierType(classReference)
      case _: Localizer => CortexTLModelType.Type.LocalizerType(classReference)
      case _: Decoder => CortexTLModelType.Type.AutoencoderType(classReference)
    }
    CortexTLModelType(cortexConsumer)
  }

  private[cv] def buildCortexTLModel(
    cvModelType: CVModelType.TL,
    classReference: ClassReference,
    featureExtractorClassReference: Option[ClassReference]
  ): CortexCVModelType =
    CortexCVModelType(
      CortexCVModelType.Type.TlModel(
        CortexTLModel(
          Some(buildCortexTLConsumer(cvModelType.consumer, classReference)),
          featureExtractorClassReference
        )
      )
    )

  private[cv] def buildCortexCustomModel(
    classReference: ClassReference
  ): CortexCVModelType = {
    CortexCVModelType(
      CortexCVModelType.Type.CustomModel(
        CortexCustomModel(
          Some(classReference)
        )
      )
    )
  }

  private[cv] def buildClassReference(
    pipelineOperator: CVTLModelPrimitive,
    operatorPackage: DCProjectPackage
  ): ClassReference = {
    ClassReference(
      moduleName = pipelineOperator.moduleName,
      className = pipelineOperator.className,
      packageLocation = operatorPackage.location
    )
  }

  private[cv] def updateAlbumVideo(
    albumId: String,
    updater: Option[Video] => Option[Video]
  ): Future[Option[WithId[Album]]] =
    albumDao.update(albumId, album => album.copy(video = updater(album.video)))


  private[cv] def loadModelMandatory(modelId: String): Future[WithId[CVModel]] =
    modelDao.get(modelId).map(_.getOrElse(throw new RuntimeException(
      s"Unexpectedly not found model $modelId in storage"
    )))

  private[cv] def assertModelStatus(model: WithId[CVModel], expectedStatus: CVModelStatus): Try[Unit] = Try {
    if (model.entity.status != expectedStatus) {
      throw new RuntimeException(
        s"Unexpected model status ${ model.entity.status } for model ${ model.id }. Expected $expectedStatus"
      )
    } else {
      ()
    }
  }

  private[cv] def updateModelStatus(modelId: String, status: CVModelStatus): Future[WithId[CVModel]] =
    modelDao.update(modelId, _.copy(status = status)).map(
      _.getOrElse(throw EntityUpdateFailedException(modelId, classOf[CVModel]))
    )

  private[cv] def failAlbum(albumId: String): Future[Option[WithId[Album]]] =
    imagesCommonService.updateAlbumStatus(albumId, AlbumStatus.Failed)

  private[cv] def activateAlbum(albumId: String): Future[Option[WithId[Album]]] =
    imagesCommonService.updateAlbumStatus(albumId, AlbumStatus.Active)

  private[services] def getCortexModelId(model: WithId[CVModel]): Try[String] =
    getIdFromCortexReference(model.entity.cortexModelReference, model.id)

  private[cv] def getCortexFeatureExtractorId(model: WithId[CVModel]): Try[String] =
    getIdFromCortexReference(model.entity.cortexFeatureExtractorReference, model.id)

  private[cv] def populateOutputAlbumIfNeeded(
    inputAlbumId: String,
    outputAlbumId: Option[String],
    predictedImages: Seq[PredictedImage]
  ): Future[Unit] = {

    def handleImagesChunk(outputAlbum: WithId[Album])(group: Iterable[PredictedImage]): Future[Unit] = {
      val predictedImagesMap = group.foldLeft(Map[String, PredictedImage]()) {
        case (currentMap, image) if image.image.isDefined =>
          // TODO reimplement this to use reference id instead of filePath (requires changes on JM side also)
          currentMap + (image.getImage.filePath -> image)
        case (currentMap, _) => currentMap
      }

      val filePaths = predictedImagesMap.keys.toSeq
      for {
        inputPictures <- pictureDao.list(
          AlbumIdIs(inputAlbumId) && FilePathIn(filePaths),
          pageNumber = 1,
          pageSize = albumPopulationBatchSize
        )
        resultPictures <- Try.sequence(inputPictures.map {
          case WithId(inputPicture, _) =>
            val predictedImage = predictedImagesMap(inputPicture.filePath)
            convertCortexTags(predictedImage.predictedTags, outputAlbum.entity.labelMode).map { predictedTags =>
              Picture(
                albumId = outputAlbum.id,
                filePath = inputPicture.filePath,
                fileName = inputPicture.fileName,
                fileSize = inputPicture.fileSize,
                caption = inputPicture.caption,
                tags = inputPicture.tags,
                predictedTags = predictedTags,
                predictedCaption = None,
                meta = Map.empty,
                originalPictureId = None,
                appliedAugmentations = None
              )
            }
        }).toFuture
        _ <- pictureDao.createMany(resultPictures)
      } yield ()
    }

    def convertCortexTags(tags: Seq[PredictedTag], labelMode: AlbumLabelMode): Try[Seq[PictureTag]] =
      Try.sequence(tags.map(tag => imagesCommonService.convertCortexTagToPictureTag(tag, labelMode)))

    outputAlbumId match {
      case Some(specifiedOutputAlbumId) =>
        for {
          outputAlbum <- imagesCommonService.getAlbumMandatory(specifiedOutputAlbumId)
          _ <- CollectionProcessing.handleIterableInParallelBatches(
            predictedImages,
            handleImagesChunk(outputAlbum),
            albumPopulationBatchSize,
            albumPopulationParallelismLevel
          )
        } yield ()
      case None =>
        Future.successful(())
    }

  }

  private[services] def populateDecoderOutputAlbum(
    inputAlbumId: String,
    outputAlbumId: String,
    predictedImages: Seq[PredictedImage]
  ): Future[Unit] = {

    def buildResultPicturesBatch(
      originalPicturesBatch: Seq[WithId[Picture]],
      resultImagesBatch: Iterable[PredictedImage]
    ): Try[Seq[Picture]] = {

      def getOriginalPicture(
        pictureId: String
      ): Try[WithId[Picture]] = Try {
        originalPicturesBatch
          .find(_.id == pictureId)
          .getOrElse(throw new RuntimeException(
            s"Unexpectedly not found original picture $pictureId for result image"
          ))
      }

      resultImagesBatch.toList.foldM(Seq.empty[Picture]) {
        case (builtPictures, resultImage) =>
          for {
            WithId(originalPicture, originalPictureId) <- getOriginalPicture(resultImage.getImage.getReferenceId)
            newName = UniqueNameGenerator.generateUniqueName[Id](
              prefix = originalPicture.fileName + "_decoded",
              suffixDelimiter = " "
            )(name => !builtPictures.exists(_.fileName == name))
          } yield {
            builtPictures :+ Picture(
              albumId = outputAlbumId,
              filePath = resultImage.getImage.filePath,
              fileName = newName,
              fileSize = resultImage.getImage.fileSize,
              caption = originalPicture.caption,
              tags = originalPicture.tags,
              originalPictureId = Some(originalPictureId),
              appliedAugmentations = None,
              predictedCaption = None,
              predictedTags = Seq.empty,
              meta = originalPicture.meta
            )
          }
      }

    }

    def handleImagesChunk(group: Iterable[PredictedImage]): Future[Unit] = {
      for {
        originalPicturesBatch <- imagesCommonService.getPictures(
          inputAlbumId,
          group.map(_.getImage.getReferenceId).toSeq
        )
        resultPicturesBatch <- buildResultPicturesBatch(originalPicturesBatch, group).toFuture
        _ <- pictureDao.createMany(resultPicturesBatch)
      } yield ()
    }


    CollectionProcessing.handleIterableInParallelBatches(
      predictedImages,
      handleImagesChunk,
      albumPopulationBatchSize,
      albumPopulationParallelismLevel
    )
  }

  private[cv] def buildAugmentationSummary(
    cortexAugmentationSummary: Option[CortexAugmentationSummary]
  ): Try[Option[Seq[AugmentationSummaryCell]]] =
    cortexAugmentationSummary match {
      case Some(summary) => Try { Some(
        summary.augmentationSummaryCells.map { cortexAugmentationSummaryCell =>
          AugmentationSummaryCell(
            augmentationParams = buildAugmentationParams(cortexAugmentationSummaryCell.getRequestedAugmentation),
            imagesCount = cortexAugmentationSummaryCell.imagesCount
          )
        }
      )}
      case None => Success(None)
    }

  private[cv] def buildAugmentationParams(
    requestedAugmentation: RequestedAugmentation
  ): AugmentationParams =
    requestedAugmentation.params match {
      case rotationParams: Params.RotationParams => RotationParams(
        rotationParams.value.angles,
        rotationParams.value.resize,
        requestedAugmentation.bloatFactor
      )
      case shearingParams: Params.ShearingParams => ShearingParams(
        shearingParams.value.angles,
        shearingParams.value.resize,
        requestedAugmentation.bloatFactor
      )
      case noisingParams: Params.NoisingParams => NoisingParams(
        noisingParams.value.noiseSignalRatios,
        requestedAugmentation.bloatFactor
      )
      case zoomInParams: Params.ZoomInParams => ZoomInParams(
        zoomInParams.value.magnifications,
        zoomInParams.value.resize,
        requestedAugmentation.bloatFactor
      )
      case zoomOutParams: Params.ZoomOutParams => ZoomOutParams(
        zoomOutParams.value.shrinkFactors,
        zoomOutParams.value.resize,
        requestedAugmentation.bloatFactor
      )
      case occlusionParams: Params.OcclusionParams => OcclusionParams(
        occlusionParams.value.occAreaFractions,
        occlusionParams.value.mode match {
          case ZERO => Zero
          case BACKGROUND => Background
          case _ => throw new RuntimeException("Occlusion mode not found.")
        },
        occlusionParams.value.isSarAlbum,
        occlusionParams.value.tarWinSize,
        requestedAugmentation.bloatFactor
      )
      case translationParams: Params.TranslationParams => TranslationParams(
        translationParams.value.translateFractions,
        translationParams.value.mode match {
          case CONSTANT => Constant
          case REFLECT => Reflect
          case _ => throw new RuntimeException("Translation mode not found.")
        },
        translationParams.value.resize,
        requestedAugmentation.bloatFactor
      )
      case blurringParams: Params.BlurringParams => BlurringParams(
        blurringParams.value.sigmaList,
        requestedAugmentation.bloatFactor
      )
      case saltPepperParams: Params.SaltPepperParams => SaltPepperParams(
        saltPepperParams.value.knockoutFractions,
        saltPepperParams.value.pepperProbability,
        requestedAugmentation.bloatFactor
      )
      case croppingParams: Params.CroppingParams => CroppingParams(
        croppingParams.value.cropAreaFractions,
        croppingParams.value.cropsPerImage,
        croppingParams.value.resize,
        requestedAugmentation.bloatFactor
      )
      case photoDistortParams: Params.PhotometricDistortParams => PhotometricDistortParams(
        PhotometricDistortAlphaBounds(
          photoDistortParams.value.getAlphaBounds.min,
          photoDistortParams.value.getAlphaBounds.max
        ),
        photoDistortParams.value.deltaMax,
        requestedAugmentation.bloatFactor
      )
      case mirroringParams: Params.MirroringParams => MirroringParams(
        mirroringParams.value.axesToFlip.map {
          case HORIZONTAL => Horizontal
          case VERTICAL => Vertical
          case BOTH => Both
          case _ => throw new RuntimeException("Invalid Mirroring axis")
        },
        requestedAugmentation.bloatFactor
      )
      case _ => throw new RuntimeException("Invalid Parameters")
    }


  private[cv] def buildCortexAutoAugmentationParams(
    params: AutomatedAugmentationParams,
    targetPrefix: Option[String]
  ): CortexAutoAugmentationParams = {
    CortexAutoAugmentationParams(
      augmentations = AlbumAugmentationUtils.convertToCortexRequestedAugmentations(params.augmentations),
      bloatFactor = Some(params.bloatFactor),
      generateSampleAlbum = params.generateSampleAlbum,
      sampleAlbumTargetPrefix = targetPrefix
    )
  }

  private[cv] def createAutoDASampleAlbum(
    inputAlbum: Album,
    userId: UUID
  ): Future[WithId[Album]] =
    for {
      albumName <- UniqueNameGenerator.generateUniqueName(
        s"${ inputAlbum.name } automated DA sample",
        " "
      )(imagesCommonService.countUserAlbums(_, None, userId).map(_ == 0))
      sampleAlbum <- albumService.create(
        name = albumName,
        labelMode = inputAlbum.labelMode,
        albumType = AlbumType.Source,
        inLibrary = false,
        ownerId = userId
      )
    } yield sampleAlbum

  private[cv] def createPredictionTable(
    baseTableName: String
  )(implicit user: User): Future[WithId[Table]] = {
    for {
      name <- UniqueNameGenerator.generateUniqueName[Future](
        prefix = baseTableName + " probability prediction",
        " "
      )(name => tableDao.count(
        OwnerIdIs(userId = user.id) && NameIs(name)
      ).map(_ == 0))
      table <- tableService.createEmptyTable(
        name = Some(name),
        tableType = TableType.Derived,
        columns = Seq.empty,
        inLibrary = false,
        user = user
      )
    } yield table
  }

  private[cv] def createPredictionTables(
    modelName: String,
    tlConsumer: CVModelType.TLConsumer,
    withTestTable: Boolean
  )(implicit user: User): Future[(Option[WithId[Table]], Option[WithId[Table]])] = {
    for {
      probabilityPredictionTable <- tlConsumer match {
        case _: CVModelType.TLConsumer.Decoder => Future.successful(None)
        case _ => createPredictionTable(
          baseTableName = modelName
        ).map(Some(_))
      }
      testProbabilityPredictionTable <- OptionT
        .fromOption[Future](probabilityPredictionTable)
        .filter(_ => withTestTable)
        .semiflatMap { _ =>
          createPredictionTable(
            baseTableName = modelName + " test"
          )
        }
        .value
    } yield (probabilityPredictionTable, testProbabilityPredictionTable)
  }

  private[cv] def buildProbabilityColumnDisplayName(className: String): String = s"$className probability"

  private[cv] def updatePredictionTableColumnsAndCalculateStatistics(
    probabilityPredictionTableId: Option[String],
    probabilityPredictionTableSchema: Option[ProbabilityPredictionTableSchema],
    modelType: CVModelType,
    userId: UUID
  ): Future[Unit] = {

    def createAreaCoordinatesColumns(areaColumns: ProbabilityPredictionAreaColumns): Seq[Column] = {
      Seq(
        areaColumns.xMinColumnName,
        areaColumns.yMinColumnName,
        areaColumns.xMaxColumnName,
        areaColumns.yMaxColumnName
      ).map { columnName =>
        Column(
          name = columnName,
          displayName = columnName,
          dataType = ColumnDataType.Integer,
          variableType = ColumnVariableType.Continuous,
          align = tableService.getColumnAlignment(ColumnDataType.Integer),
          statistics = None
        )
      }
    }

    probabilityPredictionTableId match {
      case Some(tableId) =>
        for {
          outputTable <- tableService.loadTableMandatory(tableId)
          schema = probabilityPredictionTableSchema.getOrElse(throw new RuntimeException(
            s"Unexpectedly not found schema for table '$tableId'"
          ))
          imageFileNameColumn = Column(
            name = schema.imageFileNameColumnName,
            displayName = "filename",
            dataType = ColumnDataType.String,
            variableType = ColumnVariableType.Categorical,
            align = tableService.getColumnAlignment(ColumnDataType.String),
            statistics = None
          )
          areaCoordinatesColumns = modelType match {
            case CVModelType.TL(_: Localizer, _) | CVModelType.Custom(_, Some(AlbumLabelMode.Localization)) =>
              val areaColumns = schema.areaColumns.getOrElse(throw new RuntimeException(
                "Unexpectedly not found area columns for localization model"
              ))
              createAreaCoordinatesColumns(areaColumns)
            case CVModelType.Custom(_, None) =>
              schema.areaColumns.map(createAreaCoordinatesColumns).getOrElse(Seq.empty)
            case _ => Seq.empty[Column]
          }
          probabilityColumns = schema.probabilityColumns.map { probabilityClassColumn =>
            Column(
              name = probabilityClassColumn.columnName,
              displayName = buildProbabilityColumnDisplayName(probabilityClassColumn.className),
              dataType = ColumnDataType.Double,
              variableType = ColumnVariableType.Continuous,
              align = tableService.getColumnAlignment(ColumnDataType.Double),
              statistics = None
            )
          }
          columns = imageFileNameColumn +: (areaCoordinatesColumns ++ probabilityColumns)
          _ <- tableService.updateTable(
            outputTable.id,
            _.copy(
              columns = columns,
              status = TableStatus.Active
            )
          )
          _ <- tableService.calculateColumnStatistics(
            tableId = tableId,
            columns = Some(columns),
            userId = userId
          )
        } yield ()
      case None =>
        Future.unit
    }
  }

  private[cv] def populateSampleDAAlbum(
    inputAlbumId: String,
    sampleDAAlbumId: Option[String],
    augmentedImages: Seq[AugmentationResultImage]
  ): Future[Unit] =
    sampleDAAlbumId match {
      case Some(albumId) =>
        for {
          sampleDAAlbum <- imagesCommonService.getAlbum(albumId).map(_.getOrElse(
            throw new RuntimeException(s"Unexpectedly not found sample album $albumId")
          ))
          _ <- imagesCommonService.populateAugmentedAlbum(
            inputAlbumId = inputAlbumId,
            outputAlbumId = albumId,
            labelMode = sampleDAAlbum.entity.labelMode,
            resultImages = augmentedImages
          )
        } yield ()
      case None =>
        Future.unit
    }

  private def getIdFromCortexReference(
    cortexModelReference: Option[CortexModelReference],
    modelId: String
  ): Try[String] =
    Try(cortexModelReference.map(_.cortexId).getOrElse(throw CortexModelIdNotFoundException(modelId)))

}
// scalastyle:on number.of.methods

object CVModelCommonService {

  case class AutomatedAugmentationParams(generateSampleAlbum: Boolean)

  private[cv] def isClassifier(modelType: CVModelType): Boolean = {
    modelType match {
      case CVModelType.TL(_: Classifier, _) => true
      case CVModelType.Custom(_, Some(AlbumLabelMode.Classification)) => true
      case _ => false
    }
  }

  private[cv] def inputSizeToCortexInputSize(
    inputSize: Option[CommonTrainParams.InputSize]
  ): Option[CortextInputSize] = {
    inputSize.map { input =>
      CortextInputSize(
        width = input.width,
        height = input.height
      )
    }
  }

  private[cv] def labelsOfInterestToCortexLabelsOfInterest(
    labelOfInterest: Option[Seq[LabelOfInterest]]
  ): Seq[CortexLabelOfInterest] = {
    labelOfInterest match {
      case Some(labelOfInterests) =>
        labelOfInterests.map { labelOfInterest =>
          CortexLabelOfInterest(
            label = labelOfInterest.label,
            threshold = labelOfInterest.threshold
          )
        }
      case None => Seq.empty
    }
  }

  private[cv] def addTrainParamsToRequest(
    request: CVModelTrainRequest,
    trainParams: Option[CommonTrainParams]
  ): CVModelTrainRequest =
    trainParams match {
      case Some(params) =>
        request.copy(
          inputSize = inputSizeToCortexInputSize(params.inputSize),
          labelsOfInterest = labelsOfInterestToCortexLabelsOfInterest(params.loi),
          defaultVisualThreshold = params.defaultVisualThreshold.map(_.toDouble),
          iouThreshold = params.iouThreshold.map(_.toDouble),
          featureExtractorLearningRate = params.featureExtractorLearningRate,
          modelLearningRate = params.modelLearningRate
        )
      case None =>
        request
    }

  private[cv] def buildConfusionMatrixCell(cell: CortexConfusionMatrixCell) =
    ConfusionMatrixCell(
      actualLabel = cell.actualLabelIndex,
      predictedLabel = cell.predictedLabelIndex,
      count = cell.value
    )

}
