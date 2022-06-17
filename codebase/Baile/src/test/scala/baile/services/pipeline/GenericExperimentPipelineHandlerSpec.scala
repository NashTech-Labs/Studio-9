package baile.services.pipeline

import java.time.Instant
import java.util.UUID

import baile.ExtendedBaseSpec
import baile.dao.pipeline.PipelineOperatorDao
import baile.domain.pipeline._
import baile.services.cortex.job.CortexJobService
import baile.services.process.ProcessService
import baile.RandomGenerators._
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.common.Version
import baile.domain.dcproject.DCProjectPackage
import baile.domain.experiment.{ Experiment, ExperimentStatus }
import baile.domain.pipeline.pipeline.GenericExperimentPipeline
import baile.domain.usermanagement.User
import baile.services.dcproject.DCProjectPackageService
import baile.services.pipeline.GenericExperimentPipelineHandler.GenericPipelineCreateError.{ NotAllInputsAreConnected, ParameterNotProvided }
import baile.services.pipeline.GenericExperimentPipelineHandler.UntappedStepInputs
import baile.services.usermanagement.util.TestData.SampleUser
import cortex.api.job.common.ClassReference
import baile.services.process.util.ProcessRandomGenerator.randomProcess
import cortex.api.job.pipeline.{ PipelineParam, PipelineRunRequest, PipelineStepRequest }
import org.scalatest.prop.TableDrivenPropertyChecks

class GenericExperimentPipelineHandlerSpec extends ExtendedBaseSpec with TableDrivenPropertyChecks {

  implicit val user: User = SampleUser

  trait Setup {
    val cortexJobService = mock[CortexJobService]
    val processService = mock[ProcessService]
    val pipelineOperatorDao = mock[PipelineOperatorDao]
    val packageService = mock[DCProjectPackageService]
    val pipelineService = mock[PipelineService]

    val genericExperimentPipelineHandler = new GenericExperimentPipelineHandler(
      cortexJobService = cortexJobService,
      processService = processService,
      pipelineOperatorDao = pipelineOperatorDao,
      packageService = packageService,
      pipelineService = pipelineService
    )
  }

  "GenericExperimentPipelineHandler#createPipeline" should {
    trait CreateSetup extends Setup {
      private val baseModelType = ComplexDataType("BaseModel", Seq.empty, Seq.empty)
      private val albumApiType = ComplexDataType("api.Album", Seq(baseModelType), Seq.empty)
      private val albumLibType = ComplexDataType("lib.Album", Seq(albumApiType), Seq.empty)
      private val albumAssetReferenceTypeInfo = AssetParameterTypeInfo(AssetType.Album)
      val loadAlbumOperator = WithId(
        PipelineOperator(
          name = "Load album",
          description = None,
          category = Some("OTHER"),
          className = "LoadAlbum",
          moduleName = "deepcortex.pipelines.operators.load_album",
          packageId = "deepcortex-package-id",
          inputs = Seq.empty,
          outputs = Seq(
            PipelineOperatorOutput(
              description = None,
              `type` = albumLibType
            )
          ),
          params = Seq(
            OperatorParameter(
              name = "album_id",
              description = None,
              multiple = false,
              typeInfo = albumAssetReferenceTypeInfo,
              conditions = Map.empty
            )
          )
        ),
        "load-album-id"
      )
      val saveAlbumOperator = WithId(
        PipelineOperator(
          name = "Save album",
          description = None,
          category = Some("OTHER"),
          className = "SaveAlbum",
          moduleName = "deepcortex.pipelines.operators.save_album",
          packageId = "deepcortex-package-id",
          inputs = Seq(
            PipelineOperatorInput(
              name = "album",
              description = None,
              `type` = albumLibType,
              covariate = true,
              required = true
            )
          ),
          outputs = Seq(
            PipelineOperatorOutput(
              description = None,
              `type` = PrimitiveDataType.String
            )
          ),
          params = Seq(
            OperatorParameter(
              name = "name",
              description = None,
              multiple = false,
              typeInfo = StringParameterTypeInfo(Seq.empty, Seq.empty),
              conditions = Map.empty
            ),
            OperatorParameter(
              name = "description",
              description = None,
              multiple = false,
              typeInfo = StringParameterTypeInfo(Seq.empty, Seq.empty),
              conditions = Map.empty
            ),
            OperatorParameter(
              name = "album_size",
              description = None,
              multiple = false,
              typeInfo = StringParameterTypeInfo(Seq("small", "big"), Seq.empty),
              conditions = Map.empty
            )
          )
        ),
        "save-album-id"
      )

      val loadAlbumStep = PipelineStep(
        id = "load-album-step",
        operatorId = loadAlbumOperator.id,
        inputs = Map.empty,
        params = Map(
          "album_id" -> PipelineParams.StringParam("album-123")
        ),
        coordinates = None
      )
      val loadAlbumStepOutputReference = PipelineOutputReference(
        stepId = loadAlbumStep.id,
        outputIndex = 0
      )
      val saveAlbumStep = PipelineStep(
        id = "save-album-step",
        operatorId = saveAlbumOperator.id,
        inputs = Map(
          "album" -> loadAlbumStepOutputReference
        ),
        params = Map.empty,
        coordinates = None
      )
      val dcProjectPackage = DCProjectPackage(
        ownerId = Some(UUID.randomUUID()),
        dcProjectId = Some(randomString()),
        name = randomString(),
        version = Some(Version(1, 0, 0, None)),
        location = Some(randomString()),
        created = Instant.now(),
        description = Some(randomString()),
        isPublished = true
      )
      val dcProjectPackageWithId = WithId(
        entity = dcProjectPackage,
        id = "deepcortex-package-id"
      )
      val pipelineStepsRequest = PipelineStepRequest(
        stepId = loadAlbumStep.id,
        operator = Some(ClassReference(
          packageLocation = dcProjectPackageWithId.entity.location,
          className = loadAlbumOperator.entity.className,
          moduleName = loadAlbumOperator.entity.moduleName
        )),
        inputs = Map.empty,
        params = Map(
          "album_id" -> PipelineParam(PipelineParam.Param.StringParam("album-123"))
        )
      )
      val jobId = UUID.randomUUID()
      val genericPipeline = GenericExperimentPipeline(
        steps = Seq(loadAlbumStep),
        assets = Seq()
      )
      val experimentWithId = WithId(
        Experiment(
          name = "experiment",
          ownerId = user.id,
          description = None,
          status = ExperimentStatus.Running,
          pipeline = genericPipeline,
          result = None,
          created = Instant.now(),
          updated = Instant.now()
        ),
        randomString()
      )
    }

    "successfully create pipeline" in new CreateSetup {
      pipelineService.getPipelineOperators(
        genericPipeline.steps
      ) shouldReturn future(Right(Map(loadAlbumOperator.id -> loadAlbumOperator)))
      packageService.loadPackageMandatory(
        loadAlbumOperator.entity.packageId
      ) shouldReturn future(dcProjectPackageWithId)
      cortexJobService.submitJob(
        argThat { request: PipelineRunRequest =>
          request.pipelineStepsRequest == Seq(pipelineStepsRequest)
        },
        user.id
      ) shouldReturn future(jobId)
      processService.startProcess(
        jobId = jobId,
        targetId = experimentWithId.id,
        targetType = AssetType.Experiment,
        *[Class[PipelineJobResultHandler]],
        *[PipelineJobResultHandler.Meta],
        user.id,
        *
      ) shouldReturn future(randomProcess(
        targetId = experimentWithId.id,
        targetType = AssetType.Experiment
      ))

      whenReady(
        genericExperimentPipelineHandler.validateAndCreatePipeline(
          pipeline = genericPipeline,
          experimentName = experimentWithId.entity.name,
          experimentDescription = None
        )
      ) { result =>
        val experimentHandler = result.right.value.handler
        whenReady(experimentHandler(experimentWithId.id)) { process =>
          process.entity.targetId shouldBe experimentWithId.id
        }
      }
    }

    "successfully create pipeline when a parameter with unsatisfied condition is not provided" in new CreateSetup {
      val typeInfos = Table(
        "typeInfo",
        BooleanParameterTypeInfo(Seq()),
        IntParameterTypeInfo(Seq(), Seq(), None, None, None),
        FloatParameterTypeInfo(Seq(), Seq(), None, None, None),
        StringParameterTypeInfo(Seq(), Seq()),
        AssetParameterTypeInfo(AssetType.CvModel)
      )

      forAll(typeInfos) { typeInfo =>

        val operatorWithConditions = WithId(
          PipelineOperator(
            name = "Operator",
            description = None,
            category = Some("OTHER"),
            className = "Operator",
            moduleName = "deepcortex.pipelines.operators.operator",
            packageId = "deepcortex-package-id",
            inputs = Seq.empty,
            outputs = Seq.empty,
            params = Seq(
              OperatorParameter(
                name = "bar",
                description = None,
                multiple = false,
                typeInfo = typeInfo,
                conditions = Map(
                  "foo" -> IntParameterCondition(Seq(1), None, None)
                )
              ),
              OperatorParameter(
                name = "foo",
                description = None,
                multiple = false,
                typeInfo = IntParameterTypeInfo(Seq(), Seq(), None, None, None),
                conditions = Map.empty
              )
            )
          ),
          "operator-id"
        )
        val step = PipelineStep(
          id = "operator-step",
          operatorId = operatorWithConditions.id,
          inputs = Map.empty,
          params = Map(
            "foo" -> PipelineParams.IntParam(2)
          ),
          coordinates = None
        )
        val pipeline = GenericExperimentPipeline(
          Seq(step),
          Seq()
        )
        pipelineService.getPipelineOperators(
          pipeline.steps
        ) shouldReturn future(Right(Map(
          operatorWithConditions.id -> operatorWithConditions
        )))
        packageService.loadPackageMandatory(
          operatorWithConditions.entity.packageId
        ) shouldReturn future(dcProjectPackageWithId)
        cortexJobService.submitJob(*[PipelineRunRequest], user.id) shouldReturn future(jobId)
        processService.startProcess(
          jobId = jobId,
          targetId = experimentWithId.id,
          targetType = AssetType.Experiment,
          *[Class[PipelineJobResultHandler]],
          *[PipelineJobResultHandler.Meta],
          user.id,
          *
        ) shouldReturn future(randomProcess(
          targetId = experimentWithId.id,
          targetType = AssetType.Experiment
        ))

        whenReady(
          genericExperimentPipelineHandler.validateAndCreatePipeline(
            pipeline = pipeline,
            experimentName = experimentWithId.entity.name,
            experimentDescription = None
          )
        ) { result =>
          val experimentHandler = result.right.value.handler
          whenReady(experimentHandler(experimentWithId.id)) { process =>
            process.entity.targetId shouldBe experimentWithId.id
          }
        }
      }
    }

    "fail when a parameter with satisfied condition is not provided" in new CreateSetup {
      val operatorWithConditions = WithId(
        PipelineOperator(
          name = "Operator",
          description = None,
          category = Some("OTHER"),
          className = "Operator",
          moduleName = "deepcortex.pipelines.operators.operator",
          packageId = "deepcortex-package-id",
          inputs = Seq.empty,
          outputs = Seq.empty,
          params = Seq(
            OperatorParameter(
              name = "bar",
              description = None,
              multiple = false,
              typeInfo = IntParameterTypeInfo(Seq(), Seq(), None, None, None),
              conditions = Map(
                "foo" -> IntParameterCondition(Seq(1), None, None)
              )
            ),
            OperatorParameter(
              name = "foo",
              description = None,
              multiple = false,
              typeInfo = IntParameterTypeInfo(Seq(), Seq(), None, None, None),
              conditions = Map.empty
            )
          )
        ),
        "operator-id"
      )
      val step = PipelineStep(
        id = "operator-step",
        operatorId = operatorWithConditions.id,
        inputs = Map.empty,
        params = Map(
          "foo" -> PipelineParams.IntParam(1)
        ),
        coordinates = None
      )
      val pipeline = GenericExperimentPipeline(
        Seq(step),
        Seq()
      )
      pipelineService.getPipelineOperators(
        pipeline.steps
      ) shouldReturn future(Right(Map(
        operatorWithConditions.id -> operatorWithConditions
      )))

      whenReady(
        genericExperimentPipelineHandler.validateAndCreatePipeline(
          pipeline = pipeline,
          experimentName = experimentWithId.entity.name,
          experimentDescription = None
        )
      )(_ shouldBe Left(ParameterNotProvided("operator-id", "Operator", "bar")))
    }

    "fail when required params without conditions not provided" in new CreateSetup {
      val pipeline = GenericExperimentPipeline(
        Seq(loadAlbumStep, saveAlbumStep),
        Seq()
      )
      pipelineService.getPipelineOperators(
        pipeline.steps
      ) shouldReturn future(Right(Map(
        loadAlbumOperator.id -> loadAlbumOperator,
        saveAlbumOperator.id -> saveAlbumOperator
      )))

      whenReady(
        genericExperimentPipelineHandler.validateAndCreatePipeline(
          pipeline = pipeline,
          experimentName = experimentWithId.entity.name,
          experimentDescription = None
        )
      )(_ shouldBe Left(ParameterNotProvided("save-album-id", "Save album", "name")))
    }

    "fail when inputs are not connected" in new CreateSetup {
      val pipeline = GenericExperimentPipeline(
        Seq(loadAlbumStep, saveAlbumStep.copy(inputs = Map())),
        Seq()
      )
      pipelineService.getPipelineOperators(
        pipeline.steps
      ) shouldReturn future(Right(Map(
        loadAlbumOperator.id -> loadAlbumOperator,
        saveAlbumOperator.id -> saveAlbumOperator
      )))

      whenReady(
        genericExperimentPipelineHandler.validateAndCreatePipeline(
          pipeline = pipeline,
          experimentName = experimentWithId.entity.name,
          experimentDescription = None
        )
      )(_ shouldBe Left(NotAllInputsAreConnected(
        Seq(UntappedStepInputs("save-album-step", saveAlbumOperator.entity.name, Set("album")))
      )))
    }

    "fail when param is not provided" in new CreateSetup {
      val pipeline = GenericExperimentPipeline(
        Seq(loadAlbumStep.copy(params = Map()), saveAlbumStep),
        Seq()
      )
      pipelineService.getPipelineOperators(
        pipeline.steps
      ) shouldReturn future(Right(Map(
        loadAlbumOperator.id -> loadAlbumOperator,
        saveAlbumOperator.id -> saveAlbumOperator
      )))

      whenReady(
        genericExperimentPipelineHandler.validateAndCreatePipeline(
          pipeline = pipeline,
          experimentName = experimentWithId.entity.name,
          experimentDescription = None
        )
      )(_ shouldBe Left(ParameterNotProvided(loadAlbumOperator.id, "Load album", "album_id")))
    }

  }

}
