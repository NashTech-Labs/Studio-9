package baile.dao.pipeline

import baile.BaseSpec
import baile.domain.pipeline._
import org.mongodb.scala.MongoDatabase

import scala.util.Success

class PipelineOperatorDaoSpec extends BaseSpec {

  val pipelineOperator = PipelineOperator(
    name = "some operator",
    description = Some("description of operator"),
    category = Some("OTHER"),
    className = "abcd",
    moduleName = "abcd",
    packageId = "packageId",
    inputs = Seq(PipelineOperatorInput(
      name = "abcd",
      description = Some("description of operator"),
      `type` = ComplexDataType(
        definition = "abcd",
        parents = Seq(ComplexDataType(
          definition = "xyz",
          parents = Seq.empty,
          typeArguments = Seq.empty
        )),
        typeArguments = Seq(PrimitiveDataType.Float)
      ),
      covariate = true,
      required = true
    )),
    outputs = Seq(PipelineOperatorOutput(
      description = Some("description of operator"),
      `type` = PrimitiveDataType.Float
    )),
    params = Seq(OperatorParameter(
      name = "abcd",
      description = Some("description of operator"),
      multiple = true,
      typeInfo = FloatParameterTypeInfo(
        values = Seq.empty,
        default = Seq(42f),
        min = Some(11f),
        max = None,
        step = None
      ),
      conditions = Map.empty[String, ParameterCondition]
    ))
  )

  "PipelineOperatorDao" should {
    val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
    val dao = new PipelineOperatorDao(mockedMongoDatabase)

    "convert pipeline operator to document and back" in {
      val document = dao.entityToDocument(pipelineOperator)
      val pipelineOperatorEntity = dao.documentToEntity(document)
      pipelineOperatorEntity shouldBe Success(pipelineOperator)
    }
  }

}
