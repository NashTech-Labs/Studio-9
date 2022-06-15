package baile.services.pipeline

import java.time.Instant

import baile.RandomGenerators.{ randomPath, randomString }
import baile.dao.dcproject.DCProjectPackageDao
import baile.{ ExtendedBaseSpec, RandomGenerators }
import baile.dao.pipeline.PipelineOperatorDao
import baile.daocommons.WithId
import baile.domain.common.Version
import baile.domain.dcproject.DCProjectPackage
import baile.domain.pipeline._
import baile.domain.usermanagement.User
import baile.services.dcproject.DCProjectPackageService
import baile.services.dcproject.DCProjectPackageService.DCProjectPackageServiceError
import baile.services.pipeline.PipelineOperatorService.{ PipelineOperatorInfo, PipelineOperatorServiceError }
import baile.services.usermanagement.util.TestData.SampleUser

class PipelineOperatorServiceSpec extends ExtendedBaseSpec {

  trait Setup {

    val pipelineOperatorDao: PipelineOperatorDao = mock[PipelineOperatorDao]
    val dcProjectPackageService: DCProjectPackageService = mock[DCProjectPackageService]
    val service: PipelineOperatorService = new PipelineOperatorService(
      pipelineOperatorDao,
      dcProjectPackageService
    )
    val dcProjectPackageDao: DCProjectPackageDao = mock[DCProjectPackageDao]
    implicit val user: User = SampleUser
    val operatorId = "pipelineOperatorId"
    val id = RandomGenerators.randomString()
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

    val count = 5

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
      packageId = id,
      inputs = Seq(pipelineOperatorInput),
      outputs = Seq(pipelineOperatorOutput),
      params = Seq(pipelineOperatorParam)
    ),
      operatorId
    )

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
      id
    )

    val pipelineOperatorInfo: PipelineOperatorInfo = PipelineOperatorInfo(
      pipelineOperator,
      dcProjectPackage
    )
  }

  "PipelineOperatorService#getPipelineOperator" should {

    "get pipeline operator successfully" in new Setup {
      pipelineOperatorDao.get(pipelineOperator.id) shouldReturn future(Some(pipelineOperator))
      dcProjectPackageService.get(pipelineOperator.entity.packageId) shouldReturn future(Right(dcProjectPackage))

      whenReady(service.getPipelineOperator(
        operatorId
      ))(_ shouldBe Right(pipelineOperatorInfo))
    }

    "return error response" in new Setup {
      pipelineOperatorDao.get(pipelineOperator.id) shouldReturn future(None)
      whenReady(service.getPipelineOperator(
        operatorId
      ))(_ shouldBe Left(PipelineOperatorServiceError.PipelineOperatorNotFound))
    }

  }

  "PipelineOperatorService#list" should {

    "list the pipeline operator" in new Setup {
      dcProjectPackageService.listAll(*, *) shouldReturn future(Right(Seq(dcProjectPackage)))
      pipelineOperatorDao.list(*, *, *, *) shouldReturn future(Seq(pipelineOperator))
      pipelineOperatorDao.count(*)(*) shouldReturn future(count)
      whenReady(service.list(
        Seq(),
        1,
        1,
        Some("moduleName"),
        Some("className"),
        Some("kLw4AUmeHU"),
        Some(Version(1, 2, 3, None))
      ))(_ shouldBe Right((Seq(pipelineOperatorInfo), count)))
    }

    "return error response" in new Setup {
      dcProjectPackageService.listAll(*, *) shouldReturn
        future(Left(DCProjectPackageServiceError.DCProjectPackageNotFound))
      whenReady(service.list(
        Seq("orderBy"),
        1,
        1,
        Some("moduleName"),
        Some("className"),
        Some("packageName"),
        Some(Version(1, 2, 3, None))
      ))(_ shouldBe Left(PipelineOperatorServiceError.AccessDenied))
    }

  }

}

