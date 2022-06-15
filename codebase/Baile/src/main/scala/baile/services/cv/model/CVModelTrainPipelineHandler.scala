package baile.services.cv.model

import java.time.Instant
import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.asset.Filters.{ NameIs, OwnerIdIs }
import baile.dao.cv.model.CVModelDao
import baile.dao.images.AlbumDao
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.pipeline.PipelineParams._
import baile.domain.cv.model._
import baile.domain.cv.model.tlprimitives.CVTLModelPrimitive
import baile.domain.cv.CommonTrainParams
import baile.domain.cv.pipeline.CVTLTrainPipeline
import baile.domain.cv.pipeline.FeatureExtractorParams.{
  CreateNewFeatureExtractorParams,
  UseExistingFeatureExtractorParams
}
import baile.domain.cv.result.{ CVTLTrainResult, CVTLTrainStepResult }
import baile.domain.dcproject.DCProjectPackage
import baile.domain.experiment.ExperimentStatus
import baile.domain.images.{ Album, AlbumStatus, AlbumType, Picture }
import baile.domain.pipeline.{
  AssetParameterTypeInfo,
  BooleanParameterTypeInfo,
  FloatParameterTypeInfo,
  IntParameterTypeInfo,
  OperatorParameter,
  ParameterTypeInfo,
  StringParameterTypeInfo
}
import baile.domain.table.Table
import baile.domain.usermanagement.User
import baile.services.cortex.job.CortexJobService
import baile.services.cv.CVTLModelPrimitiveService
import baile.services.cv.CVTLModelPrimitiveService.CVTLModelPrimitiveServiceError
import baile.services.cv.model.CVModelTrainPipelineHandler.CVModelCreateError._
import baile.services.cv.model.CVModelTrainPipelineHandler._
import baile.services.cv.model.CVModelTrainResultHandler.NextStepParams
import baile.services.dcproject.DCProjectPackageService
import baile.services.experiment.PipelineHandler
import baile.services.experiment.PipelineHandler.{ CreateError, PipelineCreatedResult }
import baile.services.images.{ AlbumAugmentationUtils, AlbumService, ImagesCommonService }
import baile.services.process.ProcessService
import baile.services.table.TableService
import baile.utils.TryExtensions._
import baile.utils.UniqueNameGenerator
import baile.utils.validation.Option._
import cats.data.{ EitherT, NonEmptyList, OptionT }
import cats.implicits._
import cortex.api.job.common.ClassReference
import cortex.api.job.computervision.{
  BooleanSequenceValue,
  CVModelTrainRequest,
  EvaluateRequest,
  FloatSequenceValue,
  IntSequenceValue,
  ParameterValue,
  StringSequenceValue
}

import scala.concurrent.{ ExecutionContext, Future }

class CVModelTrainPipelineHandler(
  protected val modelDao: CVModelDao,
  protected val albumDao: AlbumDao,
  protected val cvModelService: CVModelService,
  protected val cvModelCommonService: CVModelCommonService,
  protected val cvModelPrimitiveService: CVTLModelPrimitiveService,
  protected val albumService: AlbumService,
  protected val imagesCommonService: ImagesCommonService,
  protected val processService: ProcessService,
  protected val cortexJobService: CortexJobService,
  protected val packageService: DCProjectPackageService,
  protected val tableService: TableService
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends PipelineHandler[
  CVTLTrainPipeline,
  TransferLearningTrainParams,
  CVTLTrainResult,
  CVModelCreateError
] {

  type CreateErrorOr[R] = Either[CVModelCreateError, R]
  type FutureCreateErrorOr[R] = Future[CreateErrorOr[R]]

  // scalastyle:off method.length
  override protected def validatePipelineAndLoadParams(
    pipeline: CVTLTrainPipeline
  )(implicit user: User): Future[Either[CVModelCreateError, TransferLearningTrainParams]] = {

    def validateAndLoadBaseCreateParams(
      inputAlbumId: String,
      testInputAlbumId: Option[String],
      modelType: CVModelType.TLConsumer,
      pipelineParams: PipelineParams,
      autoAugmentationParams: Option[AutomatedAugmentationParams],
      trainParams: Option[CommonTrainParams]
    )(implicit user: User): FutureCreateErrorOr[BaseCVModelLoadedParams] = {

      def loadAlbum(albumId: String): FutureCreateErrorOr[WithId[Album]] =
        albumService.get(albumId).map(_.leftMap(_ => AlbumNotFound(albumId)))

      def loadTestInputAlbum(): FutureCreateErrorOr[Option[WithId[Album]]] = {
        testInputAlbumId match {
          case Some(albumId) => loadAlbum(albumId).map(_.map(Some(_)))
          case None => Future.successful(None.asRight)
        }
      }

      def validateAlbum(
        album: WithId[Album]
      ): FutureCreateErrorOr[Unit] = {
        val result = for {
          _ <- EitherT.fromEither[Future](validateAlbumIsActive(album))
          _ <- EitherT.fromEither[Future](validateAlbumAndModelTypeCompat(album.entity))
          _ <- EitherT(cvModelCommonService.validatePicturesCount[CVModelCreateError](
            album.id,
            NoPicturesInAlbum(album.id),
            onlyTagged = false
          ))
        } yield ()

        result.value
      }

      def validateAlbumIsActive(album: WithId[Album]): CreateErrorOr[Unit] = Either.cond(
        album.entity.status == AlbumStatus.Active,
        (),
        AlbumNotActive(album.id)
      )

      def validateAlbumAndModelTypeCompat(inputAlbum: Album): CreateErrorOr[Unit] =
        cvModelPrimitiveService.validateAlbumAndTLConsumerCompatibility(
          inputAlbum.labelMode,
          modelType,
          ModelTypeIsIncompatibleWithAlbum
        )

      def validateDAParams(): CreateErrorOr[Unit] =
        autoAugmentationParams.validate { augmentationParams: AutomatedAugmentationParams =>
          augmentationParams.augmentations.toList.traverse[CreateErrorOr, Unit] { augmentationParam =>
            AlbumAugmentationUtils.validateAugmentationRequestParams(
              augmentationParam,
              InvalidAugmentationRequestParamError
            )
          }.map(_ => ())
        }

      val result = for {
        _ <- EitherT.cond[Future](
          trainParams.forall(_.modelLearningRate.forall(_ > 0)),
          (),
          ModelLearningRateNotPositive
        )
        _ <- EitherT.cond[Future](
          trainParams.forall(_.featureExtractorLearningRate.forall(_ > 0)),
          (),
          FeatureExtractorLearningRateNotPositive
        )
        inputAlbum <- EitherT(loadAlbum(inputAlbumId))
        testInputAlbum <- EitherT(loadTestInputAlbum())
        primitiveWithPackage <- EitherT(
          cvModelPrimitiveService.getModelPrimitiveWithPackage(modelType.operatorId)
        ).leftMap(CVTLModelPrimitiveError)
        (modelOperator, modelPackage) = primitiveWithPackage
        specifiedPipelineParams <- EitherT.fromEither[Future](validateAndLoadCVPipelineParams(
          pipelineParams,
          modelOperator.entity.params
        ))
        _ <- EitherT.fromEither[Future](cvModelPrimitiveService.validateModelTypeAndCVTLModelPrimitiveType(
          modelType,
          modelOperator.entity.cvTLModelPrimitiveType,
          InvalidCVModelType
        ))
        _ <- EitherT(validateAlbum(inputAlbum))
        _ <- EitherT(testInputAlbum.validate(validateAlbum _))
        _ <- EitherT.fromEither[Future](validateDAParams())
      } yield BaseCVModelLoadedParams(
        inputAlbum = inputAlbum,
        testInputAlbum = testInputAlbum,
        modelConsumer = modelType,
        pipelineParams = specifiedPipelineParams,
        modelOperatorReference = OperatorReference(
          operatorId = modelOperator.id,
          moduleName = modelOperator.entity.moduleName,
          className = modelOperator.entity.className,
          packageLocation = modelPackage.entity.location,
          params = modelOperator.entity.params
        ),
        autoAugmentationParams = autoAugmentationParams,
        trainParams = trainParams
      )

      result.value
    }

    def validateAndLoadFEArchitectureOperatorReference(operatorId: String): FutureCreateErrorOr[OperatorReference] = {
      val result = for {
        primitiveWithPackage <- EitherT(
          cvModelPrimitiveService.getModelPrimitiveWithPackage(operatorId)
        ).leftMap(CVTLModelPrimitiveError)
        (architectureOperator, architecturePackage) = primitiveWithPackage
        _ <- EitherT.fromEither[Future](cvModelPrimitiveService.validateFEArchitectureCVTLModelPrimitiveType(
          architectureOperator.entity.cvTLModelPrimitiveType,
          InvalidArchitecture: CVModelCreateError
        ))
      } yield OperatorReference(
        operatorId = architectureOperator.id,
        moduleName = architectureOperator.entity.moduleName,
        className = architectureOperator.entity.className,
        packageLocation = architecturePackage.entity.location,
        params = architectureOperator.entity.params
      )
      result.value
    }

    def validateAndLoadFEParams(): FutureCreateErrorOr[FeatureExtractorLoadedParams] =
      pipeline.stepOne.feParams match {
        case CreateNewFeatureExtractorParams(featureExtractorArchitecture, pipelineParams) =>
          val result = for {
            feOperatorReference <- EitherT(validateAndLoadFEArchitectureOperatorReference(featureExtractorArchitecture))
            cvPipelineParams <- EitherT.fromEither[Future](validateAndLoadCVPipelineParams(
              params = pipelineParams,
              operatorParameters = feOperatorReference.params
            ))
          } yield NewFEParams(
            architectureOperatorReference = feOperatorReference,
            pipelineParams = cvPipelineParams
          )
          result.value
        case UseExistingFeatureExtractorParams(featureExtractorModelId, tuneFeatureExtractor) =>
          val result = for {
            featureExtractor <- EitherT(cvModelService.get(featureExtractorModelId)).leftMap { _ =>
              CVModelCreateError.FeatureExtractorNotFound
            }
            featureExtractorType <- EitherT.fromEither[Future](toTlModelType(featureExtractor.entity.`type`))
            operatorReference <- EitherT(validateAndLoadFEArchitectureOperatorReference(
              featureExtractorType.featureExtractorArchitecture
            ))
          } yield ExistingFEParams(
            architectureOperatorReference = operatorReference,
            featureExtractor = featureExtractor,
            featureExtractorType = featureExtractorType,
            tuneFeatureExtractor = tuneFeatureExtractor
          )
          result.value
      }

    def validateStepTwo(): FutureCreateErrorOr[Option[BaseCVModelLoadedParams]] =
      pipeline.stepTwo match {
        case Some(stepTwo) =>
          validateAndLoadBaseCreateParams(
            inputAlbumId = stepTwo.inputAlbumId,
            testInputAlbumId = stepTwo.testInputAlbumId,
            modelType = stepTwo.modelType,
            pipelineParams = stepTwo.modelParams,
            autoAugmentationParams = stepTwo.automatedAugmentationParams,
            trainParams = stepTwo.trainParams
          ).map(_.map(Some(_)))
        case None =>
          Future.successful(None.asRight)
      }

    def validateAndLoadCVPipelineParams(
      params: PipelineParams,
      operatorParameters: Seq[OperatorParameter]
    ): CreateErrorOr[Map[String, SpecifiedCVPipelineParam]] =
      params.foldLeft[CreateErrorOr[Map[String, SpecifiedCVPipelineParam]]](
        Map.empty.asRight
      ) { case (soFar, (name, param)) =>
        for {
          currentMap <- soFar
          operatorParam <- Either.fromOption(
            operatorParameters.find(_.name == name),
            ParameterNotFound(name)
          )
          specifiedParam = SpecifiedCVPipelineParam(
            value = param,
            definition = operatorParam
          )
        } yield currentMap + (name -> specifiedParam)
      }

    val result = for {
      stepOneBaseParams <- EitherT(validateAndLoadBaseCreateParams(
        inputAlbumId = pipeline.stepOne.inputAlbumId,
        testInputAlbumId = pipeline.stepOne.testInputAlbumId,
        modelType = pipeline.stepOne.modelType,
        pipelineParams = pipeline.stepOne.modelParams,
        autoAugmentationParams = pipeline.stepOne.automatedAugmentationParams,
        trainParams = pipeline.stepOne.trainParams
      ))
      stepOneFEParams <- EitherT(validateAndLoadFEParams())
      stepOneParams = StepOneLoadedParams(stepOneBaseParams, stepOneFEParams)
      stepTwoParams <- EitherT(validateStepTwo())
    } yield TransferLearningTrainParams(stepOneParams, stepTwoParams)

    result.value
  }
  // scalastyle:on method.length

  // scalastyle:off method.length
  override protected def createPipeline(
    params: TransferLearningTrainParams,
    pipeline: CVTLTrainPipeline,
    experimentName: String,
    experimentDescription: Option[String]
  )(implicit user: User): Future[PipelineCreatedResult[CVTLTrainPipeline]] = {

    val baseParams = params.stepOneParams.baseParams
    val featureExtractorArchitecture =
      params.stepOneParams.featureExtractorParams.architectureOperatorReference.operatorId

    def buildCortexFeatureExtractorParams(): Future[CortexFeatureExtractorParams] =
      params.stepOneParams.featureExtractorParams match {
        case NewFEParams(architectureOperatorReference, pipelineParams) =>
          Future.successful(
            CortexFeatureExtractorParams(
              feClassReference = buildClassReference(architectureOperatorReference),
              pipelineParams = pipelineParams.mapValues { param =>
                buildParameterValue(param.value, param.definition.typeInfo)
              },
              featureExtractorCortexId = None,
              tuneFeatureExtractor = false
            )
          )
        case existingFEParams: ExistingFEParams =>
          cvModelCommonService.getCortexFeatureExtractorId(existingFEParams.featureExtractor).map { cortexId =>
            CortexFeatureExtractorParams(
              feClassReference = buildClassReference(existingFEParams.architectureOperatorReference),
              pipelineParams = Map.empty,
              featureExtractorCortexId = Some(cortexId),
              tuneFeatureExtractor = existingFEParams.tuneFeatureExtractor
            )
          }.toFuture
      }

    def createAutoDASampleAlbum(): Future[Option[WithId[Album]]] =
      if (baseParams.autoAugmentationParams.fold(false)(_.generateSampleAlbum)) {
        cvModelCommonService.createAutoDASampleAlbum(
          inputAlbum = baseParams.inputAlbum.entity,
          userId = user.id
        ).map(Some(_))
      } else {
        Future.successful(None)
      }

    for {
      modelName <- generateNewName(experimentName + " Model", user.id)
      autoDASampleAlbum <- createAutoDASampleAlbum()
      inputPictures <- imagesCommonService.getPictures(
        baseParams.inputAlbum.id,
        onlyTagged = CVModelCommonService.isClassifier(CVModelType.TL(
          baseParams.modelConsumer,
          featureExtractorArchitecture
        ))
      )
      cortexFEParams <- buildCortexFeatureExtractorParams()
      outputAlbum <- createOutputAlbumIfNeeded(
        inputAlbum = baseParams.inputAlbum.entity,
        modelType = baseParams.modelConsumer,
        modelName = modelName,
        userId = user.id
      )
      testOutputAlbum <- baseParams.testInputAlbum match {
        case Some(album) => createOutputAlbumIfNeeded(
          inputAlbum = album.entity,
          modelType = baseParams.modelConsumer,
          modelName = modelName,
          userId = user.id
        )
        case None => Future.successful(None)
      }
      (probabilityPredictionTable, testProbabilityPredictionTable) <- cvModelCommonService.createPredictionTables(
        modelName = modelName,
        tlConsumer = baseParams.modelConsumer,
        withTestTable = baseParams.testInputAlbum.isDefined
      )
      (stepTwoProbabilityPredictionTable, stepTwoTestProbabilityPredictionTable) <- params.stepTwoParams match {
        case Some(stepTwoParams) =>
          cvModelCommonService.createPredictionTables(
            modelName = modelName,
            tlConsumer = stepTwoParams.modelConsumer,
            withTestTable = stepTwoParams.testInputAlbum.isDefined
          )
        case None =>
          Future.successful((None, None))
      }
      baseJobMessage = CVModelTrainRequest(
        featureExtractorId = cortexFEParams.featureExtractorCortexId,
        featureExtractorClassReference = Some(cortexFEParams.feClassReference),
        images = imagesCommonService.convertPicturesToCortexTaggedImages(inputPictures),
        filePathPrefix = imagesCommonService.getImagesPathPrefix(baseParams.inputAlbum.entity),
        modelType = Some(cvModelCommonService.buildCortexTLConsumer(
          baseParams.modelConsumer,
          buildClassReference(baseParams.modelOperatorReference)
        )),
        augmentationParams = baseParams.autoAugmentationParams.map { autoDAParams =>
          cvModelCommonService.buildCortexAutoAugmentationParams(
            params = autoDAParams,
            targetPrefix = autoDASampleAlbum.map(album => imagesCommonService.getImagesPathPrefix(album.entity))
          )
        },
        tuneFeatureExtractor = cortexFEParams.tuneFeatureExtractor,
        modelParameters = baseParams.pipelineParams.mapValues { param =>
          buildParameterValue(param.value, param.definition.typeInfo)
        },
        featureExtractorParameters = cortexFEParams.pipelineParams,
        probabilityPredictionTable = probabilityPredictionTable.map { table =>
          tableService.buildTableMeta(table.entity)
        }
      )
      jobMessage = CVModelCommonService.addTrainParamsToRequest(
        CVModelTrainRequest(
          featureExtractorId = cortexFEParams.featureExtractorCortexId,
          featureExtractorClassReference = Some(cortexFEParams.feClassReference),
          images = imagesCommonService.convertPicturesToCortexTaggedImages(inputPictures),
          filePathPrefix = imagesCommonService.getImagesPathPrefix(baseParams.inputAlbum.entity),
          modelType = Some(cvModelCommonService.buildCortexTLConsumer(
            baseParams.modelConsumer,
            buildClassReference(baseParams.modelOperatorReference)
          )),
          augmentationParams = baseParams.autoAugmentationParams.map { autoDAParams =>
            cvModelCommonService.buildCortexAutoAugmentationParams(
              params = autoDAParams,
              targetPrefix = autoDASampleAlbum.map(album => imagesCommonService.getImagesPathPrefix(album.entity))
            )
          },
          tuneFeatureExtractor = cortexFEParams.tuneFeatureExtractor,
          modelParameters = baseParams.pipelineParams.mapValues { param =>
            buildParameterValue(param.value, param.definition.typeInfo)
          },
          featureExtractorParameters = cortexFEParams.pipelineParams,
          probabilityPredictionTable = probabilityPredictionTable.map { table =>
            tableService.buildTableMeta(table.entity)
          }
        ),
        baseParams.trainParams
      )
      experimentCreatedHandler = { experimentId: String =>
        for {
          jobId <- cortexJobService.submitJob(jobMessage, user.id)
          model <- createModel(
            name = modelName,
            status = CVModelStatus.Training,
            description = experimentDescription,
            modelType = baseParams.modelConsumer,
            featureExtractorId = None,
            featureExtractorArchitecture = featureExtractorArchitecture,
            userId = user.id,
            experimentId = experimentId
          )
          process <- processService.startProcess(
            jobId = jobId,
            targetId = experimentId,
            targetType = AssetType.Experiment,
            handlerClass = classOf[CVModelTrainResultHandler],
            meta = CVModelTrainResultHandler.Meta(
              modelId = model.id,
              inputAlbumId = baseParams.inputAlbum.id,
              testInputAlbumId = baseParams.testInputAlbum.map(_.id),
              userId = user.id,
              experimentId = experimentId,
              outputAlbumId = outputAlbum.map(_.id),
              testOutputAlbumId = testOutputAlbum.map(_.id),
              autoDASampleAlbumId = autoDASampleAlbum.map(_.id),
              nextStepParams = params.stepTwoParams.map { stepTwoParams =>
                NextStepParams(
                  featureExtractorId = model.id,
                  inputAlbumId = stepTwoParams.inputAlbum.id,
                  tuneFeatureExtractor = cortexFEParams.tuneFeatureExtractor,
                  autoAugmentationParams = stepTwoParams.autoAugmentationParams,
                  testInputAlbumId = stepTwoParams.testInputAlbum.map(_.id),
                  modelParams = stepTwoParams.pipelineParams.mapValues(_.value),
                  modelType = stepTwoParams.modelConsumer,
                  probabilityPredictionTableId = stepTwoProbabilityPredictionTable.map(_.id),
                  testProbabilityPredictionTableId = stepTwoTestProbabilityPredictionTable.map(_.id),
                  trainParams = stepTwoParams.trainParams
                )
              },
              probabilityPredictionTableId = probabilityPredictionTable.map(_.id),
              testProbabilityPredictionTableId = testProbabilityPredictionTable.map(_.id),
              evaluationsMeta = List.empty
            ),
            userId = user.id
          )
        } yield process
      }
    } yield {
      PipelineCreatedResult(
        handler = experimentCreatedHandler,
        pipeline = pipeline
      )
    }

  }
  // scalastyle:on method.length

  private[model] def generateNewName(
    name: String,
    userId: UUID
  ): Future[String] =
    UniqueNameGenerator.generateUniqueName(
      name,
      " "
    )(name => modelDao.count(OwnerIdIs(userId) && NameIs(name)).map(_ == 0))

  private[model] def launchEvaluation(
    meta: CVModelEvaluateResultHandler.StepMeta,
    nextStepsMeta: List[CVModelEvaluateResultHandler.StepMeta]
  ): Future[Unit] = {

    def loadInputAlbum(): Future[WithId[Album]] =
      imagesCommonService.getAlbum(meta.testInputAlbumId).map(_.getOrElse(
        throw new RuntimeException(s"Input album ${ meta.testInputAlbumId }was not found during evaluation launch")
      ))

    def buildJobMessage(
      cortexId: String,
      modelType: CVModelType.TL,
      inputAlbum: Album,
      inputPictures: Seq[WithId[Picture]],
      modelTypeOperator: WithId[CVTLModelPrimitive],
      architectureOperator: WithId[CVTLModelPrimitive],
      architecturePackage: WithId[DCProjectPackage],
      modelTypePackage: WithId[DCProjectPackage],
      table: Option[WithId[Table]]
    ): EvaluateRequest =
      EvaluateRequest(
        modelType = Some(cvModelCommonService.buildCortexTLModel(
          modelType,
          cvModelCommonService.buildClassReference(
            modelTypeOperator.entity,
            modelTypePackage.entity
          ),
          Some(cvModelCommonService.buildClassReference(
            architectureOperator.entity,
            architecturePackage.entity
          ))
        )),
        modelId = cortexId,
        images = imagesCommonService.convertPicturesToCortexTaggedImages(inputPictures),
        filePathPrefix = imagesCommonService.getImagesPathPrefix(inputAlbum),
        probabilityPredictionTable = table.map { tableWithId =>
          tableService.buildTableMeta(tableWithId.entity)
        }
      )

    for {
      model <- cvModelCommonService.loadModelMandatory(meta.modelId)
      modelType = toTlModelType(model.entity.`type`).getOrElse(
        throw new RuntimeException(s"Invalid model type: ${ model.entity.`type` }; it must be TL")
      )
      cortexId <- cvModelCommonService.getCortexModelId(model).toFuture
      inputAlbum <- loadInputAlbum()
      inputPictures <- imagesCommonService.getPictures(
        albumId = inputAlbum.id,
        onlyTagged = CVModelCommonService.isClassifier(modelType)
      )
      architectureOperator <- cvModelPrimitiveService.loadFeatureExtractorArchitecturePrimitive(
        modelType.featureExtractorArchitecture
      )
      modelOperator <- cvModelPrimitiveService.loadTLConsumerPrimitive(modelType.consumer)
      architecturePackage <- packageService.loadPackageMandatory(architectureOperator.entity.packageId)
      modelPackage <- packageService.loadPackageMandatory(modelOperator.entity.packageId)
      table <- OptionT
        .fromOption[Future](meta.probabilityPredictionTableId)
        .semiflatMap(tableService.loadTableMandatory)
        .value
      jobMessage = buildJobMessage(
        cortexId,
        modelType,
        inputAlbum.entity,
        inputPictures,
        modelOperator,
        architectureOperator,
        architecturePackage,
        modelPackage,
        table
      )
      evaluateJobId <- cortexJobService.submitJob(jobMessage, meta.userId)
      _ <- modelDao.update(model.id, _.copy(status = CVModelStatus.Predicting))
      _ <- processService.startProcess(
        jobId = evaluateJobId,
        targetId = meta.experimentId,
        targetType = AssetType.Experiment,
        handlerClass = classOf[CVModelEvaluateResultHandler],
        meta = CVModelEvaluateResultHandler.Meta(NonEmptyList(meta, nextStepsMeta)),
        userId = meta.userId
      )
    } yield ()
  }

  private[model] def createOutputAlbumIfNeeded(
    inputAlbum: Album,
    modelType: CVModelType.TLConsumer,
    modelName: String,
    userId: UUID
  ): Future[Option[WithId[Album]]] = modelType match {
    case _: CVModelType.TLConsumer.Decoder => Future.successful(None)
    case _ => cvModelCommonService.createOutputAlbum(
      picturesPrefix = inputAlbum.picturesPrefix,
      namePrefix = s"$modelName ${ inputAlbum.name } OUT",
      labelMode = inputAlbum.labelMode,
      albumType = AlbumType.TrainResults,
      userId = userId
    ).map(Some(_))
  }

  private[model] def createModel(
    name: String,
    status: CVModelStatus,
    description: Option[String],
    modelType: CVModelType.TLConsumer,
    featureExtractorId: Option[String],
    featureExtractorArchitecture: String,
    userId: UUID,
    experimentId: String
  ): Future[WithId[CVModel]] = {
    val now = Instant.now

    modelDao.create(_ =>
      CVModel(
        ownerId = userId,
        name = name,
        created = now,
        updated = now,
        status = status,
        description = description,
        inLibrary = false,
        cortexFeatureExtractorReference = None,
        cortexModelReference = None,
        `type` = CVModelType.TL(
          consumer = modelType,
          featureExtractorArchitecture = featureExtractorArchitecture
        ),
        classNames = None,
        featureExtractorId = featureExtractorId,
        experimentId = Some(experimentId)
      )
    )
  }

  private[model] def buildParameterValue(param: PipelineParam, typeInfo: ParameterTypeInfo): ParameterValue = {
    val parameterValue = param match {
      case StringParam(value) => ParameterValue.Value.StringValue(value)
      case IntParam(value) => ParameterValue.Value.IntValue(value)
      case FloatParam(value) => ParameterValue.Value.FloatValue(value)
      case BooleanParam(value) => ParameterValue.Value.BooleanValue(value)
      case StringParams(values) => ParameterValue.Value.StringValues(StringSequenceValue(values))
      case IntParams(values) => ParameterValue.Value.IntValues(IntSequenceValue(values))
      case FloatParams(values) => ParameterValue.Value.FloatValues(FloatSequenceValue(values))
      case BooleanParams(values) => ParameterValue.Value.BooleanValues(BooleanSequenceValue(values))
      case EmptySeqParam =>
        typeInfo match {
          case _: BooleanParameterTypeInfo => ParameterValue.Value.BooleanValues(BooleanSequenceValue(List.empty))
          case _: FloatParameterTypeInfo => ParameterValue.Value.FloatValues(FloatSequenceValue(List.empty))
          case _: IntParameterTypeInfo => ParameterValue.Value.IntValues(IntSequenceValue(List.empty))
          case _: StringParameterTypeInfo => ParameterValue.Value.StringValues(StringSequenceValue(List.empty))
          case _: AssetParameterTypeInfo => ParameterValue.Value.StringValues(StringSequenceValue(List.empty))
        }
    }

    ParameterValue(parameterValue)
  }

  private[model] def updateOutputEntitiesOnSuccess(
    result: CVTLTrainResult
  ): Future[Unit] = {

    def unlockOutputAlbum(albumId: Option[String]): Future[Unit] =
      albumId match {
        case Some(id) => cvModelCommonService.activateAlbum(id).map(_ => ())
        case None => Future.unit
      }

    def completeStep(step: CVTLTrainStepResult): Future[Unit] =
      for {
        _ <- cvModelCommonService.updateModelStatus(step.modelId, CVModelStatus.Active)
        _ <- unlockOutputAlbum(step.outputAlbumId)
        _ <- unlockOutputAlbum(step.testOutputAlbumId)
        _ <- unlockOutputAlbum(step.autoAugmentationSampleAlbumId)
      } yield ()

    for {
      _ <- completeStep(result.stepOne)
      _ <- result.stepTwo.fold(Future.unit)(completeStep)
    } yield ()
  }

  private[model] def updateOutputEntitiesOnNoSuccess[S <: ExperimentStatus: NonSuccessfulTerminalStatus](
    result: CVTLTrainResult,
    status: S
  ): Future[Unit] = {

    def failOutputAlbum(albumId: Option[String]): Future[Unit] =
      albumId match {
        case Some(id) => cvModelCommonService.failAlbum(id).map(_ => ())
        case None => Future.unit
      }

    def failStep(step: CVTLTrainStepResult): Future[Unit] =
      for {
        _ <- cvModelCommonService.updateModelStatus(
          step.modelId,
          implicitly[NonSuccessfulTerminalStatus[S]].correspondingModelStatus
        )
        _ <- failOutputAlbum(step.outputAlbumId)
        _ <- failOutputAlbum(step.testOutputAlbumId)
        _ <- failOutputAlbum(step.autoAugmentationSampleAlbumId)
      } yield ()

    for {
      _ <- failStep(result.stepOne)
      _ <- result.stepTwo.fold(Future.unit)(failStep)
    } yield ()
  }

  private def buildClassReference(operatorReference: OperatorReference): ClassReference = {
    ClassReference(
      moduleName = operatorReference.moduleName,
      className = operatorReference.className,
      packageLocation = operatorReference.packageLocation
    )
  }

  private[cv] def toTlModelType(modelType: CVModelType): Either[CVModelCreateError, CVModelType.TL] =
    modelType match {
      case tl: CVModelType.TL => tl.asRight[CVModelCreateError]
      case _ => InvalidCVModelType.asLeft
    }

}

object CVModelTrainPipelineHandler {

  sealed trait CVModelCreateError extends CreateError
  object CVModelCreateError {
    case class AlbumNotFound(id: String) extends CVModelCreateError
    case class AlbumNotActive(id: String) extends CVModelCreateError
    case class NoPicturesInAlbum(albumId: String) extends CVModelCreateError
    case class InvalidAugmentationRequestParamError(message: String) extends CVModelCreateError
    case object FeatureExtractorNotInLibrary extends CVModelCreateError
    case object FeatureExtractorNotFound extends CVModelCreateError
    case object AlbumLabelModeNotCompatible extends CVModelCreateError
    case object ArchitectureNotSupported extends CVModelCreateError
    case object ModelTypeIsIncompatibleWithAlbum extends CVModelCreateError
    case object ConsumerIsIncompatibleWithArchitecture extends CVModelCreateError
    case object InvalidCVModelType extends CVModelCreateError
    case class CVTLModelPrimitiveError(error: CVTLModelPrimitiveServiceError) extends CVModelCreateError
    case object InvalidParams extends CVModelCreateError
    case object OutputAlbumNotFound extends CVModelCreateError
    case object ModelNotFound extends CVModelCreateError
    case class ParameterNotFound(name: String) extends CVModelCreateError
    case object CantDeleteRunningModel extends CVModelCreateError
    case object InvalidArchitecture extends CVModelCreateError
    case object ModelLearningRateNotPositive extends CVModelCreateError
    case object FeatureExtractorLearningRateNotPositive extends CVModelCreateError
  }

  case class TransferLearningTrainParams(
    stepOneParams: StepOneLoadedParams,
    stepTwoParams: Option[BaseCVModelLoadedParams]
  )

  case class StepOneLoadedParams(
    baseParams: BaseCVModelLoadedParams,
    featureExtractorParams: FeatureExtractorLoadedParams
  )

  case class BaseCVModelLoadedParams(
    inputAlbum: WithId[Album],
    testInputAlbum: Option[WithId[Album]],
    modelConsumer: CVModelType.TLConsumer,
    pipelineParams: Map[String, SpecifiedCVPipelineParam],
    modelOperatorReference: OperatorReference,
    autoAugmentationParams: Option[AutomatedAugmentationParams],
    trainParams: Option[CommonTrainParams]
  )

  sealed trait FeatureExtractorLoadedParams {
    val architectureOperatorReference: OperatorReference
  }

  case class NewFEParams(
    architectureOperatorReference: OperatorReference,
    pipelineParams: Map[String, SpecifiedCVPipelineParam]
  ) extends FeatureExtractorLoadedParams

  case class ExistingFEParams(
    architectureOperatorReference: OperatorReference,
    featureExtractor: WithId[CVModel],
    featureExtractorType: CVModelType.TL,
    tuneFeatureExtractor: Boolean
  ) extends FeatureExtractorLoadedParams

  case class OperatorReference(
    operatorId: String,
    moduleName: String,
    className: String,
    packageLocation: Option[String],
    params: Seq[OperatorParameter]
  )

  private case class CortexFeatureExtractorParams(
    feClassReference: ClassReference,
    pipelineParams: Map[String, ParameterValue],
    featureExtractorCortexId: Option[String],
    tuneFeatureExtractor: Boolean
  )

  private[model] case class SpecifiedCVPipelineParam(
    value: PipelineParam,
    definition: OperatorParameter
  )

  private[model] sealed trait NonSuccessfulTerminalStatus[S] {
    val correspondingModelStatus: CVModelStatus
  }

  private[model] implicit val CancelledNonSuccessfulTerminalStatus: NonSuccessfulTerminalStatus[
    ExperimentStatus.Cancelled.type
  ] = new NonSuccessfulTerminalStatus[ExperimentStatus.Cancelled.type] {
    override val correspondingModelStatus: CVModelStatus = CVModelStatus.Cancelled
  }

  private[model] implicit val ErrorNonSuccessfulTerminalStatus: NonSuccessfulTerminalStatus[
    ExperimentStatus.Error.type
  ] = new NonSuccessfulTerminalStatus[ExperimentStatus.Error.type] {
    override val correspondingModelStatus: CVModelStatus = CVModelStatus.Error
  }

}
