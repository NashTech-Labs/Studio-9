package cortex.jobmaster.orion.service.domain

import java.io.File
import java.util.UUID

import cortex.api.job.project.`package`.ParameterCondition.Condition.FloatCondition
import cortex.api.job.project.`package`._
import cortex.jobmaster.jobs.job.TestUtils
import cortex.jobmaster.modules.SettingsModule
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.testkit.{ FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.{ FlatSpec, Matchers }

class ProjectPackageServiceTest extends FlatSpec
  with Matchers
  with FutureTestUtils
  with WithS3AndLocalScheduler
  with SettingsModule {

  lazy val s3AccessParams = S3AccessParams(
    bucket      = baseBucket,
    accessKey   = accessKey,
    secretKey   = secretKey,
    region      = "",
    endpointUrl = Some(fakeS3Endpoint)
  )
  val baseProjectPath = "cortex-job-master/python_project"

  lazy val projectPackagerService = ProjectPackagerService(taskScheduler, s3AccessParams, this)

  override def beforeAll(): Unit = {
    super.beforeAll()

    //upload package files to fake s3
    new File("../test_data/python_project/football").listFiles().filter(_.isFile).toList.foreach { f =>
      TestUtils.copyToS3(fakeS3Client, baseBucket, s"$baseProjectPath/football/${f.getName}", f.getAbsolutePath)
    }
  }

  "ProjectPackageService" should "package a project collecting required info" in {
    val expectedCVTLModelPrimitives = Set(
      CVTLModelPrimitive(
        name        = "test detector",
        description = Some("test detector description"),
        moduleName  = "football.test_primitives",
        className   = "TestDetector",
        `type`      = OperatorType.Detector,
        params      = List(
          OperatorParameter(
            name        = "some_str",
            description = None,
            multiple    = false,
            conditions  = Map("value_name" -> ParameterCondition(FloatCondition(FloatParameterCondition(List(1.0F, 2.5F), Some(3.5F), None)))),
            typeInfo    = OperatorParameter.TypeInfo.StringInfo(StringParameter(List(), List()))
          ),
          OperatorParameter(
            name        = "value_name",
            description = None,
            multiple    = false,
            conditions  = Map(),
            typeInfo    = OperatorParameter.TypeInfo.FloatInfo(FloatParameter(List(), List()))
          )
        ),
        isNeural    = true
      ),
      CVTLModelPrimitive(
        name        = "test non-neural classifier",
        description = Some("test non-neural classifier description"),
        moduleName  = "football.test_primitives",
        className   = "TestNonNeuralClassifier",
        `type`      = OperatorType.Classifier,
        params      = List(),
        isNeural    = false
      )
    )

    val expectedPipelineOperators = Set(
      PipelineOperator(
        name        = "test pipeline operator",
        description = Some("test pipeline operator description"),
        moduleName  = "football.test_pipeline_operator",
        className   = "TestPipelineOperator",
        inputs      = List(
          PipelineOperatorInput(
            name        = "x",
            description = None,
            `type`      = Some(PipelineDataType(PipelineDataType.DataType.PrimitiveDataType(PrimitiveDataType.Integer))),
            covariate   = true,
            required    = true
          ),
          PipelineOperatorInput(
            name        = "baz",
            description = None,
            `type`      = Some(PipelineDataType(PipelineDataType.DataType.ComplexDataType(ComplexDataType(
              definition = "football.test_pipeline_operator.Baz",
              parents    = List(
                ComplexDataType(
                  definition = "football.test_pipeline_operator.Foo"
                )
              )
            )))),
            covariate   = false,
            required    = true
          ),
          PipelineOperatorInput(
            name        = "qux",
            description = Some("a qux"),
            `type`      = Some(PipelineDataType(PipelineDataType.DataType.ComplexDataType(ComplexDataType(
              definition = "football.test_pipeline_operator.Qux",
              parents    = List(
                ComplexDataType(
                  definition = "football.test_pipeline_operator.Baz",
                  parents    = List(
                    ComplexDataType(
                      definition = "football.test_pipeline_operator.Foo"
                    )
                  )
                ),
                ComplexDataType(
                  definition = "football.test_pipeline_operator.Bar"
                )
              )
            )))),
            covariate   = true,
            required    = false
          )
        ),
        outputs     = List(
          PipelineOperatorOutput(
            description = Some("loaded model"),
            `type`      = Some(
              PipelineDataType(PipelineDataType.DataType.ComplexDataType(ComplexDataType(
                definition = "football.test_pipeline_operator.MyModel"
              )))
            )
          )
        ),
        params      = List(
          OperatorParameter(
            name        = "album",
            description = None,
            multiple    = false,
            conditions  = Map(),
            typeInfo    = OperatorParameter.TypeInfo.AssetInfo(AssetParameter(AssetType.Album))
          )
        )
      ),
      PipelineOperator(
        name        = "get random number",
        description = None,
        moduleName  = "football.get_random_number",
        className   = "GetRandomNumber",
        inputs      = Nil,
        outputs     = List(
          PipelineOperatorOutput(
            description = None,
            `type`      = Some(
              PipelineDataType(PipelineDataType.DataType.PrimitiveDataType(PrimitiveDataType.Integer))
            )
          )
        ),
        params      = Nil
      )
    )

    val projectPackageRequest = ProjectPackageRequest(
      projectFilesPath = baseProjectPath,
      name             = "test-project",
      version          = "0.1",
      targetPrefix     = "cortex-job-master/output_path"
    )

    val (result, _) = projectPackagerService.pack(jobId, projectPackageRequest).await()

    result.packageLocation shouldBe "cortex-job-master/output_path/test_project-0.1-py3-none-any.whl"
    result.cvTlModelPrimitives.toSet shouldBe expectedCVTLModelPrimitives
    result.pipelineOperators.toSet shouldBe expectedPipelineOperators
  }

  private def jobId = {
    UUID.randomUUID().toString
  }
}
