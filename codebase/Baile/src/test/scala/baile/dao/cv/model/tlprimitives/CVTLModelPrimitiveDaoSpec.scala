package baile.dao.cv.model.tlprimitives

import baile.BaseSpec
import baile.domain.cv.model.tlprimitives.{ CVTLModelPrimitive, CVTLModelPrimitiveType }
import baile.domain.pipeline._
import org.mongodb.scala.MongoDatabase

import scala.util.Success

class CVTLModelPrimitiveDaoSpec extends BaseSpec {

  val cvTLModelPrimitive = CVTLModelPrimitive(
    packageId = "packageId",
    name = "VGG17",
    description = Some("description of operator"),
    moduleName = "model.train",
    className = "VGG17Train",
    cvTLModelPrimitiveType = CVTLModelPrimitiveType.UTLP,
    params = Seq(
      OperatorParameter(
        name = "delta",
        multiple = false,
        description = Some("delta value"),
        typeInfo = FloatParameterTypeInfo(
          values = Seq.empty,
          default = Seq(42f),
          min = Some(11f),
          max = None,
          step = None
        ),
        conditions = Map.empty[String, ParameterCondition]
      ),
      OperatorParameter(
        name = "mode",
        multiple = false,
        description = Some("train mode"),
        conditions = Map.empty[String, ParameterCondition],
        typeInfo = StringParameterTypeInfo(
          values = Seq("strong, simple, weak, complex"),
          default = Seq("simple")
        )
      ),
      OperatorParameter(
        name = "threshold",
        description = None,
        multiple = false,
        conditions = Map(
          "mode" -> StringParameterCondition(Seq("complex"))
        ),
        typeInfo = IntParameterTypeInfo(
          values = Seq.empty,
          default = Seq.empty,
          min = Some(0),
          max = None,
          step = Some(1)
        )
      ),
      OperatorParameter(
        name = "foo",
        description = Some("Football"),
        multiple = false,
        typeInfo = BooleanParameterTypeInfo(
          default = Seq.empty
        ),
        conditions = Map(
          "threshold" -> IntParameterCondition(
            values = Seq.empty,
            min = Some(1),
            max = None
          ),
          "delta" -> FloatParameterCondition(
            values = Seq.empty,
            min = None,
            max = Some(1e5f)
          ),
          "foo" -> BooleanParameterCondition(
            value = true  // ;)
          )
        )
      )
    ),
    isNeural = true
  )

  "CVTLModelPrimitiveDao" should {
    val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
    val dao = new CVTLModelPrimitiveDao(mockedMongoDatabase)

    "convert pipeline operator to document and back" in {
      val document = dao.entityToDocument(cvTLModelPrimitive)
      val cvTLModelPrimitiveEntity = dao.documentToEntity(document)
      cvTLModelPrimitiveEntity shouldBe Success(cvTLModelPrimitive)
    }
  }

}
