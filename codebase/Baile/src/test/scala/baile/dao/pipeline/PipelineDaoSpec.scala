package baile.dao.pipeline

import java.time.Instant
import java.util.UUID

import baile.BaseSpec
import baile.domain.pipeline.PipelineParams._
import baile.domain.pipeline._
import org.mongodb.scala.MongoDatabase

import scala.util.Success

class PipelineDaoSpec extends BaseSpec {
  val pipeline = Pipeline(
    name = "new_pipeline",
    ownerId = UUID.randomUUID,
    status = PipelineStatus.Idle,
    created = Instant.now,
    updated = Instant.now,
    inLibrary = true,
    description = Some("description of pipeline"),
    steps = Seq(
      PipelineStepInfo(
        step = PipelineStep(
          id = "step1",
          operatorId = "operatorId",
          inputs = Map(
            "operatorName" -> PipelineOutputReference(
              "stepId",
              1
            )
          ),
          params = Map(
            "paramName1" -> StringParam("paramter1"),
            "paramName2" -> StringParams(Seq("paramter1")),
            "paramName3" -> IntParam(1),
            "paramName4" -> IntParams(Seq(1,2)),
            "paramName5" -> FloatParam(1.0F),
            "paramName6" -> FloatParams(Seq(1.0F,2.0F)),
            "paramName7" -> BooleanParam(true),
            "paramName8" -> BooleanParams(Seq(true,false)),
            "paramName9" -> EmptySeqParam
          ),
          coordinates = Some(PipelineCoordinates(
            x = 22,
            y = 24
          ))
        ),
        pipelineParameters = Map(
          "paramName10" -> "Pipeline Parameter 10",
          "paramName11" -> "Pipeline Parameter 11"
        )
      )
    )
  )

  "PipelineDao" should {
    val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
    val dao = new PipelineDao(mockedMongoDatabase)

    "convert pipeline to document and back" in {
      val document = dao.entityToDocument(pipeline)
      val pipelineEntity = dao.documentToEntity(document)
      pipelineEntity shouldBe Success(pipeline)
    }
  }
}
