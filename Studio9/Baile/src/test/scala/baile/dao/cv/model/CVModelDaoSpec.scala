package baile.dao.cv.model

import java.time.Instant
import java.util.UUID

import baile.BaseSpec
import baile.domain.common.CortexModelReference
import baile.domain.cv.model._
import org.mongodb.scala.MongoDatabase

import scala.util.Success

class CVModelDaoSpec extends BaseSpec {

  "CVModelDao" should {
    val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
    val dao: CVModelDao = new CVModelDao(mockedMongoDatabase)

    val model = CVModel(
      ownerId = UUID.randomUUID,
      name = "test Model",
      created = Instant.now(),
      updated = Instant.now(),
      status = CVModelStatus.Training,
      cortexFeatureExtractorReference = Some(CortexModelReference("cid", "cfilepath")),
      cortexModelReference = Some(CortexModelReference("cid", "cfilepath")),
      `type` = CVModelType.TL(CVModelType.TLConsumer.Classifier("FCN_1"), "SCAE"),
      classNames = Some(Seq("foo")),
      featureExtractorId = Some("fe_id"),
      description = Some("Model description"),
      inLibrary = false,
      experimentId = None
    )

    "convert model to document and back" in {
      val document = dao.entityToDocument(model)
      val restoredModel = dao.documentToEntity(document)

      restoredModel shouldBe Success(model)
    }
  }

}
