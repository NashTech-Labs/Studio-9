package baile.services.pipeline

import java.time.Instant

import baile.RandomGenerators.{ randomPath, randomString }
import baile.dao.pipeline.PipelineDao
import baile.daocommons.WithId
import baile.domain.dcproject.DCProjectPackage
import baile.domain.pipeline.PipelineParams._
import baile.domain.pipeline._
import baile.domain.usermanagement.User
import baile.services.asset.sharing.AssetSharingService
import baile.services.pipeline.PipelineOperatorService.{ PipelineOperatorInfo, PipelineOperatorServiceError }
import baile.services.pipeline.PipelineService.PipelineServiceError._
import baile.services.pipeline.PipelineValidationError._
import baile.services.project.ProjectService
import baile.services.usermanagement.util.TestData.SampleUser
import baile.{ ExtendedBaseSpec, RandomGenerators }
import cats.implicits._

class PipelineServiceSpec extends ExtendedBaseSpec {

  trait Setup {

    val pipelineDao: PipelineDao = mock[PipelineDao]
    val projectService: ProjectService = mock[ProjectService]
    val pipelineOperatorService: PipelineOperatorService = mock[PipelineOperatorService]
    val assetSharingService = mock[AssetSharingService]
    val service: PipelineService = new PipelineService(
      pipelineDao,
      pipelineOperatorService,
      projectService,
      assetSharingService
    )

    implicit val user: User = SampleUser

    val dcProjectPackage = WithId(
      DCProjectPackage(
        ownerId = Some(user.id),
        dcProjectId = None,
        name = randomString(),
        version = None,
        location = Some(randomPath()),
        created = Instant.now(),
        description = None,
        isPublished = true
      ),
      randomString()
    )
    val pipelineStepsInputKeyName: String = RandomGenerators.randomString()
    val pipelineOperatorInput = PipelineOperatorInput(
      name = pipelineStepsInputKeyName,
      description = Some(RandomGenerators.randomString()),
      `type` = ComplexDataType(
        definition = RandomGenerators.randomString(),
        parents = Seq(ComplexDataType(
          definition = RandomGenerators.randomString(),
          parents = Seq.empty,
          typeArguments = Seq.empty
        )),
        typeArguments = Seq(PrimitiveDataType.Float)
      ),
      covariate = RandomGenerators.randomBoolean(),
      required = RandomGenerators.randomBoolean()
    )

    val pipelineOperatorOutput = PipelineOperatorOutput(
      description = RandomGenerators.randomOpt(RandomGenerators.randomString()),
      `type` = PrimitiveDataType.Float
    )

    val pipelineOperatorParam = OperatorParameter(
      name = "some-param",
      description = Some(RandomGenerators.randomString()),
      multiple = RandomGenerators.randomBoolean(),
      typeInfo = FloatParameterTypeInfo(
        values = Seq.empty,
        default = Seq(42f),
        min = Some(11f),
        max = None,
        step = None
      ),
      conditions = Map.empty[String, ParameterCondition]
    )

    val pipelineOperator = WithId(PipelineOperator(
      name = RandomGenerators.randomString(),
      description = None,
      category = Some("OTHER"),
      className = RandomGenerators.randomString(),
      moduleName = RandomGenerators.randomString(),
      packageId = RandomGenerators.randomString(),
      inputs = Seq(pipelineOperatorInput),
      outputs = Seq(pipelineOperatorOutput),
      params = Seq(pipelineOperatorParam)
    ),
      RandomGenerators.randomString()
    )
    val pipelineStep = PipelineStep(
      id = RandomGenerators.randomString(),
      operatorId = pipelineOperator.id,
      inputs = Map(),
      params = Map.empty[String, PipelineParam],
      coordinates = None
    )
    val pipelineStepInfo = PipelineStepInfo(
      step = pipelineStep,
      pipelineParameters = Map("some-param" -> "Some parameter")
    )

    val pipeline = WithId(Pipeline(
      name = RandomGenerators.randomString(),
      ownerId = user.id,
      status = PipelineStatus.Idle,
      created = Instant.now,
      updated = Instant.now,
      inLibrary = RandomGenerators.randomBoolean(),
      description = Some(RandomGenerators.randomString()),
      steps = Seq(pipelineStepInfo)
    ),
      RandomGenerators.randomString()
    )
  }

  "PipelineService#create" should {

    "create pipeline successfully" in new Setup {
      pipelineOperatorService.getPipelineOperator(pipelineOperator.id) shouldReturn
        future(PipelineOperatorInfo(pipelineOperator, dcProjectPackage).asRight)
      pipelineDao.count(*) shouldReturn future(0)
      pipelineDao.create(*[String => Pipeline]) shouldReturn future(pipeline)
      whenReady(service.create(
        Some(pipeline.entity.name),
        pipeline.entity.description,
        None,
        pipeline.entity.steps
      ))(_ shouldBe pipeline.asRight)
    }

    "return error when Input steps is not correct" in new Setup {
      val invalidInputStep = PipelineStep(
        id = pipelineStep.id,
        operatorId = pipelineStep.operatorId,
        inputs = Map(),
        params = Map(
          "paramName1" -> StringParam("paramter1")
        ),
        coordinates = None
      )
      val invalidInputStepInfo = PipelineStepInfo(
        step = invalidInputStep,
        pipelineParameters = Map.empty
      )
      pipelineOperatorService.getPipelineOperator(invalidInputStep.operatorId) shouldReturn
        future(PipelineOperatorInfo(pipelineOperator, dcProjectPackage).asRight)
      pipelineDao.count(*) shouldReturn future(0)
      whenReady(service.create(
        Some(pipeline.entity.name),
        pipeline.entity.description,
        None,
        Seq(invalidInputStepInfo)
      ))(_ shouldBe PipelineStepValidationError(PipelineParamNotFound("paramName1")).asLeft)
    }

    "return error for operator not found" in new Setup {
      pipelineOperatorService.getPipelineOperator(pipelineStep.operatorId) shouldReturn
        future(PipelineOperatorServiceError.PipelineOperatorNotFound.asLeft)
      pipelineDao.count(*) shouldReturn future(0)
      whenReady(service.create(
        Some(pipeline.entity.name),
        pipeline.entity.description,
        None,
        pipeline.entity.steps
      ))(_ shouldBe PipelineOperatorError(PipelineOperatorServiceError.PipelineOperatorNotFound).asLeft)
    }

  }

  "PipelineService#update" should {

    "update pipeline name and steps" in new Setup {
      val newName = "newNames"
      val updatedPipelineName: WithId[Pipeline] = pipeline.copy(entity = pipeline.entity.copy(name = newName))

      pipelineOperatorService.getPipelineOperator(pipelineStep.operatorId) shouldReturn
        future(PipelineOperatorInfo(pipelineOperator, dcProjectPackage).asRight)
      pipelineDao.get(pipeline.id) shouldReturn future(Some(pipeline))
      pipelineDao.count(*) shouldReturn future(0)
      pipelineDao.update(pipeline.id, *) shouldReturn future(Some(updatedPipelineName))

      whenReady(service.update(pipeline.id, Some(newName), None, Some(Seq(pipelineStepInfo))))(
        _ shouldBe updatedPipelineName.asRight)
    }

    "return error when different steps with the same ID are passed" in new Setup {
      val anotherPipelineStep = PipelineStep(
        id = pipelineStep.id,
        operatorId = RandomGenerators.randomString(),
        inputs = Map(
          pipelineStepsInputKeyName -> PipelineOutputReference(
            RandomGenerators.randomString(),
            RandomGenerators.randomInt(1, 10)
          )
        ),
        params = Map.empty[String, PipelineParam],
        coordinates = None
      )
      val anotherPipelineStepInfo = PipelineStepInfo(
        step = anotherPipelineStep,
        pipelineParameters = Map.empty
      )
      pipelineOperatorService.getPipelineOperator(pipelineStep.operatorId) shouldReturn
        future(PipelineOperatorInfo(pipelineOperator, dcProjectPackage).asRight)
      pipelineOperatorService.getPipelineOperator(anotherPipelineStep.operatorId) shouldReturn
        future(PipelineOperatorInfo(pipelineOperator, dcProjectPackage).asRight)

      whenReady(service.update(pipeline.id, Some("newName"), None, Some(Seq(pipelineStepInfo, anotherPipelineStepInfo)))
      )(_ shouldBe PipelineStepValidationError(StepsIdsAreNotUnique).asLeft)

    }

    "error update pipeline name with existing name " in new Setup {
      val newName = "newNames"
      pipelineOperatorService.getPipelineOperator(pipelineOperator.id) shouldReturn
        future(PipelineOperatorInfo(pipelineOperator, dcProjectPackage).asRight)
      pipelineDao.get(pipeline.id) shouldReturn future(Some(pipeline))
      pipelineDao.count(*) shouldReturn future(1)

      whenReady(service.update(pipeline.id, Some(newName), None, Some(Seq(pipelineStepInfo))))(
        _ shouldBe NameIsTaken.asLeft)
    }

  }

}
