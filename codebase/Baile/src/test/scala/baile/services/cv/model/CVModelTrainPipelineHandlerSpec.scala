package baile.services.cv.model

import java.time.Instant
import java.util.UUID

import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.cv.model.CVModelDao
import baile.dao.images.AlbumDao
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.common.{ CortexModelReference, Version }
import baile.domain.pipeline.PipelineParams.{ BooleanParam, FloatParam, IntParam, StringParam }
import baile.domain.cv.model.tlprimitives.{ CVTLModelPrimitive, CVTLModelPrimitiveType }
import baile.domain.cv.model.{ CVModelType, _ }
import baile.domain.cv.pipeline.FeatureExtractorParams.{ CreateNewFeatureExtractorParams, UseExistingFeatureExtractorParams }
import baile.domain.cv.pipeline.{ CVTLTrainPipeline, CVTLTrainStep1Params, CVTLTrainStep2Params }
import baile.domain.cv.result.{ CVTLTrainResult, CVTLTrainStepResult }
import baile.domain.dcproject.DCProjectPackage
import baile.domain.experiment.{ Experiment, ExperimentStatus }
import baile.domain.images._
import baile.domain.pipeline.{
  BooleanParameterTypeInfo,
  FloatParameterTypeInfo,
  IntParameterTypeInfo,
  OperatorParameter,
  StringParameterTypeInfo
}
import baile.domain.table.{ Table, TableStatisticsStatus, TableStatus, TableType }
import baile.domain.usermanagement.User
import baile.services.cortex.job.CortexJobService
import baile.services.cortex.job.SupportedCortexJobTypes.SupportedCortexJobType
import baile.services.cv.CVTLModelPrimitiveService
import baile.services.cv.model.CVModelEvaluateResultHandler.StepMeta
import baile.services.cv.model.CVModelRandomGenerator._
import baile.services.dcproject.DCProjectPackageService
import baile.services.images.util.ImagesRandomGenerator
import baile.services.images.util.ImagesRandomGenerator._
import baile.services.images.{ AlbumService, ImagesCommonService }
import baile.services.process.ProcessService
import baile.services.process.util.ProcessRandomGenerator._
import baile.services.table.TableService
import baile.services.table.util.TableRandomGenerator
import baile.services.usermanagement.util.TestData.SampleUser
import cats.data.NonEmptyList
import cats.implicits._
import cortex.api.job.album.common.TaggedImage
import cortex.api.job.computervision.{ CVModelTrainRequest, EvaluateRequest, TLModelType }
import cortex.api.job.common.{ ClassReference => CortexClassReference }

import scala.util.Try


class CVModelTrainPipelineHandlerSpec extends ExtendedBaseSpec {

  implicit val user: User = SampleUser

  trait Setup {

    val modelDao = mock[CVModelDao]
    val albumDao = mock[AlbumDao]
    val cvModelService = mock[CVModelService]
    val cvModelCommonService = mock[CVModelCommonService]
    val cvModelPrimitiveService = mock[CVTLModelPrimitiveService]
    val albumService = mock[AlbumService]
    val imagesCommonService = mock[ImagesCommonService]
    val processService = mock[ProcessService]
    val cortexJobService = mock[CortexJobService]
    val packageService = mock[DCProjectPackageService]
    val tableService = mock[TableService]

    val pipelineHandler = new CVModelTrainPipelineHandler(
      modelDao = modelDao,
      albumDao = albumDao,
      cvModelService = cvModelService,
      cvModelCommonService = cvModelCommonService,
      cvModelPrimitiveService = cvModelPrimitiveService,
      albumService = albumService,
      imagesCommonService = imagesCommonService,
      processService = processService,
      cortexJobService = cortexJobService,
      packageService = packageService,
      tableService = tableService
    )

  }

  "CVModelTrainPipelineHandler#validateAndCreatePipeline" should {

    trait CreateSetup extends Setup {

      val inputAlbum = ImagesRandomGenerator.randomAlbum(
        labelMode = AlbumLabelMode.Classification,
        status = AlbumStatus.Active
      )
      val outputAlbum = ImagesRandomGenerator.randomAlbum()

      val predictionTable = TableRandomGenerator.randomTable()

      val operatorPackage = WithId(
        DCProjectPackage(
          ownerId = None,
          dcProjectId = None,
          name = "package",
          version = Some(Version(1, 2, 1, None)),
          location = None,
          created = Instant.now,
          description = None,
          isPublished = true
        ),
        randomString()
      )
      val modelOperator = WithId(
        CVTLModelPrimitive(
          packageId = operatorPackage.id,
          name = "operator",
          description = None,
          moduleName = "module1",
          className = "class1",
          cvTLModelPrimitiveType = CVTLModelPrimitiveType.Classifier,
          params = Seq(
            OperatorParameter(
              "param1",
              description = None,
              multiple = false,
              typeInfo = StringParameterTypeInfo(Seq.empty, Seq.empty),
              conditions = Map.empty
            ),
            OperatorParameter(
              "param3",
              description = None,
              multiple = false,
              typeInfo = FloatParameterTypeInfo(Seq.empty, Seq.empty, None, None, None),
              conditions = Map.empty
            ),
            OperatorParameter(
              "param2",
              description = None,
              multiple = false,
              typeInfo = BooleanParameterTypeInfo(Seq.empty),
              conditions = Map.empty
            ),
            OperatorParameter(
              "param4",
              description = None,
              multiple = false,
              typeInfo = IntParameterTypeInfo(Seq.empty, Seq.empty, None, None, None),
              conditions = Map.empty
            )
          ),
          isNeural = true
        ),
        randomString()
      )
      val feOperator = WithId(
        CVTLModelPrimitive(
          packageId = operatorPackage.id,
          name = "fe_operator",
          description = None,
          moduleName = "module1",
          className = "class3",
          cvTLModelPrimitiveType = CVTLModelPrimitiveType.UTLP,
          params = Seq.empty,
          isNeural = true
        ),
        randomString()
      )
      val modelType = CVModelType.TLConsumer.Classifier(modelOperator.id)
      val featureExtractorModelType = randomTLModelType(feOperator.id)

      val model = CVModelRandomGenerator.randomModel()

    }

    "successfully create pipeline (one step, create new FE)" in new CreateSetup {

      val pipeline = CVTLTrainPipeline(
        stepOne = CVTLTrainStep1Params(
          feParams = CreateNewFeatureExtractorParams(
            featureExtractorArchitecture = feOperator.id,
            pipelineParams = Map.empty
          ),
          modelType = modelType,
          modelParams = Map(
            "param1" -> StringParam("val1"),
            "param2" -> BooleanParam(true),
            "param3" -> FloatParam(42.1f),
            "param4" -> IntParam(4)
          ),
          inputAlbumId = inputAlbum.id,
          testInputAlbumId = None,
          automatedAugmentationParams = None,
          trainParams = None
        ),
        stepTwo = None
      )

      val experiment = WithId(
        Experiment(
          name = "experiment",
          ownerId = user.id,
          description = None,
          status = ExperimentStatus.Running,
          pipeline = pipeline,
          result = None,
          created = Instant.now(),
          updated = Instant.now()
        ),
        randomString()
      )

      albumService.get(inputAlbum.id)(user) shouldReturn future(inputAlbum.asRight)
      cvModelPrimitiveService.getModelPrimitiveWithPackage(modelType.operatorId) shouldReturn
        future((modelOperator, operatorPackage).asRight)
      cvModelPrimitiveService.validateModelTypeAndCVTLModelPrimitiveType(
        modelType,
        modelOperator.entity.cvTLModelPrimitiveType,
        *
      ) shouldReturn ().asRight
      cvModelPrimitiveService.validateAlbumAndTLConsumerCompatibility(
        inputAlbum.entity.labelMode,
        modelType,
        *
      ) shouldReturn ().asRight
      cvModelCommonService.validatePicturesCount(inputAlbum.id, *, *) shouldReturn future(().asRight)
      cvModelPrimitiveService.getModelPrimitiveWithPackage(feOperator.id) shouldReturn
        future((feOperator, operatorPackage).asRight)
      cvModelPrimitiveService.validateFEArchitectureCVTLModelPrimitiveType(CVTLModelPrimitiveType.UTLP, *) shouldReturn
        ().asRight
      modelDao.count(*) shouldReturn future(0)
      imagesCommonService.getPictures(inputAlbum.id, *[Boolean]) shouldReturn future(Seq.empty)
      cvModelCommonService.createOutputAlbum(
        *,
        *,
        *,
        AlbumType.TrainResults,
        *,
        false,
        AlbumStatus.Saving,
        user.id
      ) shouldReturn future(outputAlbum)
      cvModelCommonService.createPredictionTables(*, *, *)(*) shouldReturn future((Some(predictionTable), None))
      tableService.buildTableMeta(*) shouldCall realMethod
      modelDao.create(*) shouldReturn future(model)
      cvModelCommonService.buildCortexTLConsumer(modelType, *) shouldReturn TLModelType.defaultInstance
      imagesCommonService.convertPicturesToCortexTaggedImages(*) shouldReturn Seq.empty
      imagesCommonService.getImagesPathPrefix(inputAlbum.entity) shouldReturn randomString()
      cortexJobService.submitJob(*[CVModelTrainRequest], user.id) shouldReturn future(UUID.randomUUID())
      processService.startProcess(
        *,
        experiment.id,
        AssetType.Experiment,
        *[Class[CVModelTrainResultHandler]],
        *[CVModelTrainResultHandler.Meta],
        user.id
      ) shouldReturn future(randomProcess(
        targetId = experiment.id,
        targetType = AssetType.Experiment
      ))

      whenReady(
        pipelineHandler.validateAndCreatePipeline(
          pipeline = pipeline,
          experimentName = experiment.entity.name,
          experimentDescription = None
        )
      ) { result =>
        val experimentHandler = result.right.value.handler
        whenReady(experimentHandler(experiment.id)) { process =>
          process.entity.targetId shouldBe experiment.id
        }
      }

    }

    "successfully create pipeline (one step, use existing FE)" in new CreateSetup {

      val featureExtractor = randomModel(
        status = CVModelStatus.Active,
        modelType = featureExtractorModelType
      )

      val pipeline = CVTLTrainPipeline(
        stepOne = CVTLTrainStep1Params(
          feParams = UseExistingFeatureExtractorParams(
            featureExtractorModelId = featureExtractor.id,
            tuneFeatureExtractor = true
          ),
          modelType = modelType,
          modelParams = Map.empty,
          inputAlbumId = inputAlbum.id,
          testInputAlbumId = None,
          automatedAugmentationParams = None,
          trainParams = None
        ),
        stepTwo = None
      )

      val experiment = WithId(
        Experiment(
          name = "experiment",
          ownerId = user.id,
          description = None,
          status = ExperimentStatus.Running,
          pipeline = pipeline,
          result = None,
          created = Instant.now(),
          updated = Instant.now()
        ),
        randomString()
      )

      cvModelService.get(featureExtractor.id) shouldReturn future(featureExtractor.asRight)
      albumService.get(inputAlbum.id)(user) shouldReturn future(inputAlbum.asRight)
      cvModelPrimitiveService.getModelPrimitiveWithPackage(modelType.operatorId) shouldReturn
        future((modelOperator, operatorPackage).asRight)
      cvModelPrimitiveService.validateModelTypeAndCVTLModelPrimitiveType(
        modelType,
        modelOperator.entity.cvTLModelPrimitiveType,
        *
      ) shouldReturn ().asRight
      cvModelPrimitiveService.validateAlbumAndTLConsumerCompatibility(
        inputAlbum.entity.labelMode,
        modelType,
        *
      ) shouldReturn ().asRight
      cvModelCommonService.validatePicturesCount(inputAlbum.id, *, *) shouldReturn future(().asRight)
      cvModelPrimitiveService.getModelPrimitiveWithPackage(feOperator.id) shouldReturn
        future((feOperator, operatorPackage).asRight)
      cvModelPrimitiveService.validateFEArchitectureCVTLModelPrimitiveType(CVTLModelPrimitiveType.UTLP, *) shouldReturn
        ().asRight
      modelDao.count(*) shouldReturn future(0)
      imagesCommonService.getPictures(inputAlbum.id, *[Boolean]) shouldReturn future(Seq.empty)
      cvModelCommonService.createOutputAlbum(
        *,
        *,
        *,
        AlbumType.TrainResults,
        *,
        false,
        AlbumStatus.Saving,
        user.id
      ) shouldReturn future(outputAlbum)
      cvModelCommonService.createPredictionTables(*, *, *)(*) shouldReturn future((Some(predictionTable), None))
      tableService.buildTableMeta(*) shouldCall realMethod
      cvModelCommonService.getCortexFeatureExtractorId(featureExtractor) shouldReturn Try(randomString())
      modelDao.create(*) shouldReturn future(model)
      cvModelCommonService.buildCortexTLConsumer(modelType, *) shouldReturn TLModelType.defaultInstance
      imagesCommonService.convertPicturesToCortexTaggedImages(*) shouldReturn Seq.empty
      imagesCommonService.getImagesPathPrefix(inputAlbum.entity) shouldReturn randomString()
      cortexJobService.submitJob(*[CVModelTrainRequest], user.id) shouldReturn future(UUID.randomUUID())
      processService.startProcess(
        *,
        experiment.id,
        AssetType.Experiment,
        *[Class[CVModelTrainResultHandler]],
        *[CVModelTrainResultHandler.Meta],
        user.id
      ) shouldReturn future(randomProcess(
        targetId = experiment.id,
        targetType = AssetType.Experiment
      ))

      whenReady(
        pipelineHandler.validateAndCreatePipeline(
          pipeline = pipeline,
          experimentName = experiment.entity.name,
          experimentDescription = None
        )
      ) { result =>
        val experimentHandler = result.right.value.handler
        whenReady(experimentHandler(experiment.id)) { process =>
          process.entity.targetId shouldBe experiment.id
        }
      }

    }

    "successfully create pipeline (two steps, create new FE)" in new CreateSetup {

      object stepTwo {
        val inputAlbum = ImagesRandomGenerator.randomAlbum(
          labelMode = AlbumLabelMode.Classification,
          status = AlbumStatus.Active
        )
        val outputAlbum = ImagesRandomGenerator.randomAlbum()
      }

      val pipeline = CVTLTrainPipeline(
        stepOne = CVTLTrainStep1Params(
          feParams = CreateNewFeatureExtractorParams(
            featureExtractorArchitecture = feOperator.id,
            pipelineParams = Map.empty
          ),
          modelType = modelType,
          modelParams = Map.empty,
          inputAlbumId = inputAlbum.id,
          testInputAlbumId = None,
          automatedAugmentationParams = None,
          trainParams = None
        ),
        stepTwo = Some(CVTLTrainStep2Params(
          tuneFeatureExtractor = false,
          modelType = modelType,
          modelParams = Map.empty,
          inputAlbumId = stepTwo.inputAlbum.id,
          testInputAlbumId = None,
          automatedAugmentationParams = None,
          trainParams = None
        ))
      )

      val experiment = WithId(
        Experiment(
          name = "experiment",
          ownerId = user.id,
          description = None,
          status = ExperimentStatus.Running,
          pipeline = pipeline,
          result = None,
          created = Instant.now(),
          updated = Instant.now()
        ),
        randomString()
      )

      albumService.get(inputAlbum.id)(user) shouldReturn future(inputAlbum.asRight)
      cvModelPrimitiveService.getModelPrimitiveWithPackage(modelType.operatorId) shouldReturn
        future((modelOperator, operatorPackage).asRight)
      cvModelPrimitiveService.validateModelTypeAndCVTLModelPrimitiveType(
        modelType,
        modelOperator.entity.cvTLModelPrimitiveType,
        *
      ) shouldReturn ().asRight
      cvModelPrimitiveService.validateAlbumAndTLConsumerCompatibility(
        inputAlbum.entity.labelMode,
        modelType,
        *
      ) shouldReturn ().asRight
      cvModelCommonService.validatePicturesCount(inputAlbum.id, *, *) shouldReturn future(().asRight)
      cvModelPrimitiveService.getModelPrimitiveWithPackage(feOperator.id) shouldReturn
        future((feOperator, operatorPackage).asRight)
      cvModelPrimitiveService.validateFEArchitectureCVTLModelPrimitiveType(CVTLModelPrimitiveType.UTLP, *) shouldReturn ().asRight
      modelDao.count(*) shouldReturn future(0)
      imagesCommonService.getPictures(inputAlbum.id, *[Boolean]) shouldReturn future(Seq.empty)
      cvModelCommonService.createOutputAlbum(
        *,
        *,
        *,
        AlbumType.TrainResults,
        *,
        false,
        AlbumStatus.Saving,
        user.id
      ) shouldReturn future(outputAlbum)
      cvModelCommonService.createPredictionTables(*, *, *)(*) shouldReturn future((Some(predictionTable), None))
      tableService.buildTableMeta(*) shouldCall realMethod
      modelDao.create(*) shouldReturn future(model)
      cvModelCommonService.buildCortexTLConsumer(modelType, *) shouldReturn TLModelType.defaultInstance
      imagesCommonService.convertPicturesToCortexTaggedImages(*) shouldReturn Seq.empty
      imagesCommonService.getImagesPathPrefix(inputAlbum.entity) shouldReturn randomString()
      cortexJobService.submitJob(*[CVModelTrainRequest], user.id) shouldReturn future(UUID.randomUUID())
      processService.startProcess(
        *,
        experiment.id,
        AssetType.Experiment,
        *[Class[CVModelTrainResultHandler]],
        *[CVModelTrainResultHandler.Meta],
        user.id
      ) shouldReturn future(randomProcess(
        targetId = experiment.id,
        targetType = AssetType.Experiment
      ))
      albumService.get(stepTwo.inputAlbum.id) shouldReturn future(inputAlbum.asRight)

      whenReady(
        pipelineHandler.validateAndCreatePipeline(
          pipeline = pipeline,
          experimentName = experiment.entity.name,
          experimentDescription = None
        )
      ) { result =>
        val experimentHandler = result.right.value.handler
        whenReady(experimentHandler(experiment.id)) { process =>
          process.entity.targetId shouldBe experiment.id
        }
      }

    }

    "successfully create pipeline (two steps, use existing FE)" in new CreateSetup {

      object stepTwo {
        val inputAlbum = ImagesRandomGenerator.randomAlbum(
          labelMode = AlbumLabelMode.Classification,
          status = AlbumStatus.Active
        )
        val outputAlbum = ImagesRandomGenerator.randomAlbum()
      }

      val featureExtractor = randomModel(
        status = CVModelStatus.Active,
        modelType = featureExtractorModelType
      )

      val pipeline = CVTLTrainPipeline(
        stepOne = CVTLTrainStep1Params(
          feParams = UseExistingFeatureExtractorParams(
            featureExtractorModelId = featureExtractor.id,
            tuneFeatureExtractor = true
          ),
          modelType = modelType,
          modelParams = Map.empty,
          inputAlbumId = inputAlbum.id,
          testInputAlbumId = None,
          automatedAugmentationParams = None,
          trainParams = None
        ),
        stepTwo = Some(CVTLTrainStep2Params(
          tuneFeatureExtractor = false,
          modelType = modelType,
          modelParams = Map.empty,
          inputAlbumId = stepTwo.inputAlbum.id,
          testInputAlbumId = None,
          automatedAugmentationParams = None,
          trainParams = None
        ))
      )

      val experiment = WithId(
        Experiment(
          name = "experiment",
          ownerId = user.id,
          description = None,
          status = ExperimentStatus.Running,
          pipeline = pipeline,
          result = None,
          created = Instant.now(),
          updated = Instant.now()
        ),
        randomString()
      )

      cvModelService.get(featureExtractor.id) shouldReturn future(featureExtractor.asRight)
      albumService.get(inputAlbum.id)(user) shouldReturn future(inputAlbum.asRight)
      cvModelPrimitiveService.getModelPrimitiveWithPackage(modelType.operatorId) shouldReturn
        future((modelOperator, operatorPackage).asRight)
      cvModelPrimitiveService.validateModelTypeAndCVTLModelPrimitiveType(
        modelType,
        modelOperator.entity.cvTLModelPrimitiveType,
        *
      ) shouldReturn ().asRight
      cvModelPrimitiveService.validateAlbumAndTLConsumerCompatibility(
        inputAlbum.entity.labelMode,
        modelType,
        *
      ) shouldReturn ().asRight
      cvModelCommonService.validatePicturesCount(inputAlbum.id, *, *) shouldReturn future(().asRight)
      cvModelPrimitiveService.getModelPrimitiveWithPackage(feOperator.id) shouldReturn
        future((feOperator, operatorPackage).asRight)
      cvModelPrimitiveService.validateFEArchitectureCVTLModelPrimitiveType(CVTLModelPrimitiveType.UTLP, *) shouldReturn ().asRight
      modelDao.count(*) shouldReturn future(0)
      imagesCommonService.getPictures(inputAlbum.id, *[Boolean]) shouldReturn future(Seq.empty)
      cvModelCommonService.createOutputAlbum(
        *,
        *,
        *,
        AlbumType.TrainResults,
        *,
        false,
        AlbumStatus.Saving,
        user.id
      ) shouldReturn future(outputAlbum)
      cvModelCommonService.createPredictionTables(*, *, *)(*) shouldReturn future((Some(predictionTable), None))
      tableService.buildTableMeta(*) shouldCall realMethod
      cvModelCommonService.getCortexFeatureExtractorId(featureExtractor) shouldReturn Try(randomString())
      modelDao.create(*) shouldReturn future(model)
      cvModelCommonService.buildCortexTLConsumer(modelType, *) shouldReturn TLModelType.defaultInstance
      imagesCommonService.convertPicturesToCortexTaggedImages(*) shouldReturn Seq.empty
      imagesCommonService.getImagesPathPrefix(inputAlbum.entity) shouldReturn randomString()
      cortexJobService.submitJob(*[CVModelTrainRequest], user.id) shouldReturn future(UUID.randomUUID())
      processService.startProcess(
        *,
        experiment.id,
        AssetType.Experiment,
        *[Class[CVModelTrainResultHandler]],
        *[CVModelTrainResultHandler.Meta],
        user.id
      ) shouldReturn future(randomProcess(
        targetId = experiment.id,
        targetType = AssetType.Experiment
      ))
      albumService.get(stepTwo.inputAlbum.id) shouldReturn future(inputAlbum.asRight)

      whenReady(
        pipelineHandler.validateAndCreatePipeline(
          pipeline = pipeline,
          experimentName = experiment.entity.name,
          experimentDescription = None
        )
      ) { result =>
        val experimentHandler = result.right.value.handler
        whenReady(experimentHandler(experiment.id)) { process =>
          process.entity.targetId shouldBe experiment.id
        }
      }

    }

  }

  "CVModelTrainPipelineHandler#launchEvaluation" should {

    val packageId = randomString()
    val dcProjectPackageSample = WithId(DCProjectPackage(
      name = "packageName",
      created = Instant.now(),
      ownerId = Some(UUID.randomUUID),
      location = Some("/package/"),
      version = Some(Version(1, 0, 0, None)),
      dcProjectId = Some("projectId"),
      description = Some("package description"),
      isPublished = true
    ), packageId)
    val architecture = WithId(
      CVTLModelPrimitive(
        packageId = packageId,
        name = "SCAE",
        isNeural = true,
        moduleName = "ml_lib.feature_extractors.backbones",
        className = "SCAE",
        cvTLModelPrimitiveType = CVTLModelPrimitiveType.UTLP,
        params = Seq(),
        description = None
      ),
      randomString()
    )
    val classifier = WithId(
      CVTLModelPrimitive(
        packageId = packageId,
        name = "FCN_1",
        isNeural = true,
        moduleName = "ml_lib.classifiers.backbones",
        className = "FCN_1",
        cvTLModelPrimitiveType = CVTLModelPrimitiveType.Classifier,
        params = Seq(),
        description = None
      ),
      randomString()
    )
    val consumer = CVModelType.TLConsumer.Classifier(classifier.id)
    val modelType = CVModelType.TL(consumer, architecture.id)
    val model = randomModel(
      cortexModelReference = Some(CortexModelReference(
        randomString(),
        randomString()
      )),
      modelType = modelType
    )
    val testInputAlbum = randomAlbum()
    val experimentId = randomString()
    val tableSample = WithId(
      Table(
        name = "table-name",
        ownerId = user.id,
        repositoryId = "table-schema",
        databaseId = "database-id",
        columns = Nil,
        status = TableStatus.Active,
        created = Instant.now(),
        updated = Instant.now(),
        `type` = TableType.Derived,
        inLibrary = false,
        tableStatisticsStatus = TableStatisticsStatus.Done,
        description = None
      ),
      "test_probability_prediction_table_id"
    )

    val stepMeta = StepMeta(
      modelId = model.id,
      experimentId = experimentId,
      testInputAlbumId = testInputAlbum.id,
      userId = user.id,
      probabilityPredictionTableId = Some(tableSample.id)
    )

    "successfully create test output album, update model and start evaluation" in new Setup {
      val cortexJobId = UUID.randomUUID()
      cvModelCommonService.loadModelMandatory(model.id) shouldReturn future(model)
      cvModelCommonService.getCortexModelId(model) shouldReturn Try(model.entity.cortexModelReference.get.cortexId)
      imagesCommonService.getAlbum(testInputAlbum.id) shouldReturn future(Some(testInputAlbum))
      imagesCommonService.getPictures(
        testInputAlbum.id,
        onlyTagged = true
      ) shouldReturn future(Seq.fill(5)(ImagesRandomGenerator.randomPicture()))
      imagesCommonService.convertPicturesToCortexTaggedImages(*) shouldReturn Seq.fill(5)(TaggedImage())
      future(classifier) willBe returned by cvModelPrimitiveService.loadTLConsumerPrimitive(consumer)
      future(architecture) willBe returned by
        cvModelPrimitiveService.loadFeatureExtractorArchitecturePrimitive(architecture.id)
      packageService.loadPackageMandatory(packageId) shouldReturn future(dcProjectPackageSample)
      tableService.loadTableMandatory(tableSample.id) shouldReturn future(tableSample)
      tableService.buildTableMeta(*) shouldCall realMethod
      CortexClassReference(
        moduleName = classifier.entity.moduleName,
        className = classifier.entity.className,
        packageLocation = dcProjectPackageSample.entity.location
      ) willBe returned by cvModelCommonService.buildClassReference(classifier.entity, dcProjectPackageSample.entity)
      CortexClassReference(
        moduleName = architecture.entity.moduleName,
        className = architecture.entity.className,
        packageLocation = dcProjectPackageSample.entity.location
      ) willBe returned by cvModelCommonService.buildClassReference(
        architecture.entity,
        dcProjectPackageSample.entity
      )
      cvModelCommonService.buildCortexTLConsumer(consumer, *) shouldCall realMethod
      cvModelCommonService.buildCortexTLModel(modelType, *, *) shouldCall realMethod
      imagesCommonService.getImagesPathPrefix(testInputAlbum.entity) shouldReturn "prefix"
      cortexJobService.submitJob(
        *[EvaluateRequest],
        user.id
      )(implicitly[SupportedCortexJobType[EvaluateRequest]]) shouldReturn future(cortexJobId)
      modelDao.update(model.id, *)(*) shouldReturn future(Some(model))
      processService.startProcess(
        jobId = cortexJobId,
        targetId = experimentId,
        targetType = AssetType.Experiment,
        handlerClass = classOf[CVModelEvaluateResultHandler],
        meta = CVModelEvaluateResultHandler.Meta(NonEmptyList.one(stepMeta)),
        userId = user.id
      ) shouldReturn future(randomProcess())

      pipelineHandler.launchEvaluation(
        meta = stepMeta,
        nextStepsMeta = List.empty
      ).futureValue
    }

    "fail when ImagesCommonService does not return album" in new Setup {
      cvModelCommonService.loadModelMandatory(model.id) shouldReturn future(model)
      cvModelCommonService.getCortexModelId(model) shouldReturn Try(model.entity.cortexModelReference.get.cortexId)
      imagesCommonService.getAlbum(testInputAlbum.id) shouldReturn future(None)
      pipelineHandler.launchEvaluation(
        meta = stepMeta,
        nextStepsMeta = List.empty
      ).failed.futureValue should not be a[NullPointerException]
    }

  }

  "CVModelTrainPipelineHandler#updateOutputEntitiesOnSuccess" should {

    "make all albums and model active" in new Setup {

      val model = randomModel(inLibrary = false)
      val outputAlbum = randomAlbum(inLibrary = false)
      val testOutputAlbum = randomAlbum(inLibrary = false)
      val autoAugmentationSampleAlbum = randomAlbum(inLibrary = false)

      val result = CVTLTrainResult(
        stepOne = CVTLTrainStepResult(
          modelId = model.id,
          outputAlbumId = Some(outputAlbum.id),
          testOutputAlbumId = Some(testOutputAlbum.id),
          autoAugmentationSampleAlbumId = Some(autoAugmentationSampleAlbum.id),
          summary = None,
          testSummary = None,
          augmentationSummary = None,
          trainTimeSpentSummary = None,
          evaluateTimeSpentSummary = None,
          probabilityPredictionTableId = None,
          testProbabilityPredictionTableId = None
        ),
        stepTwo = Some(CVTLTrainStepResult(
          modelId = model.id,
          outputAlbumId = Some(outputAlbum.id),
          testOutputAlbumId = Some(testOutputAlbum.id),
          autoAugmentationSampleAlbumId = Some(autoAugmentationSampleAlbum.id),
          summary = None,
          testSummary = None,
          augmentationSummary = None,
          trainTimeSpentSummary = None,
          evaluateTimeSpentSummary = None,
          probabilityPredictionTableId = None,
          testProbabilityPredictionTableId = None
        ))
      )

      cvModelCommonService.updateModelStatus(result.stepOne.modelId, CVModelStatus.Active) shouldReturn future(model)
      cvModelCommonService.activateAlbum(outputAlbum.id) shouldReturn future(Some(outputAlbum))
      cvModelCommonService.activateAlbum(testOutputAlbum.id) shouldReturn future(Some(testOutputAlbum))
      cvModelCommonService.activateAlbum(autoAugmentationSampleAlbum.id) shouldReturn future(Some(autoAugmentationSampleAlbum))

      pipelineHandler.updateOutputEntitiesOnSuccess(result).futureValue
    }

  }

  "CVModelTrainPipelineHandler#updateOutputEntitiesOnNoSuccess" should {

    "make all albums failed and model cancelled" in new Setup {

      val model = randomModel(inLibrary = false)
      val outputAlbum = randomAlbum(inLibrary = false)
      val testOutputAlbum = randomAlbum(inLibrary = false)
      val autoAugmentationSampleAlbum = randomAlbum(inLibrary = false)

      val result = CVTLTrainResult(
        stepOne = CVTLTrainStepResult(
          modelId = model.id,
          outputAlbumId = Some(outputAlbum.id),
          testOutputAlbumId = Some(testOutputAlbum.id),
          autoAugmentationSampleAlbumId = Some(autoAugmentationSampleAlbum.id),
          summary = None,
          testSummary = None,
          augmentationSummary = None,
          trainTimeSpentSummary = None,
          evaluateTimeSpentSummary = None,
          probabilityPredictionTableId = None,
          testProbabilityPredictionTableId = None
        ),
        stepTwo = Some(CVTLTrainStepResult(
          modelId = model.id,
          outputAlbumId = Some(outputAlbum.id),
          testOutputAlbumId = Some(testOutputAlbum.id),
          autoAugmentationSampleAlbumId = Some(autoAugmentationSampleAlbum.id),
          summary = None,
          testSummary = None,
          augmentationSummary = None,
          trainTimeSpentSummary = None,
          evaluateTimeSpentSummary = None,
          probabilityPredictionTableId = None,
          testProbabilityPredictionTableId = None
        ))
      )

      cvModelCommonService.updateModelStatus(result.stepOne.modelId, CVModelStatus.Cancelled) shouldReturn future(model)
      cvModelCommonService.failAlbum(outputAlbum.id) shouldReturn future(Some(outputAlbum))
      cvModelCommonService.failAlbum(testOutputAlbum.id) shouldReturn future(Some(testOutputAlbum))
      cvModelCommonService.failAlbum(autoAugmentationSampleAlbum.id) shouldReturn future(Some(autoAugmentationSampleAlbum))

      pipelineHandler.updateOutputEntitiesOnNoSuccess(result, ExperimentStatus.Cancelled).futureValue
    }

  }


}
