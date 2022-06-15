package baile.dao.dataset

import java.time.Instant
import java.util.UUID

import baile.BaseSpec
import baile.domain.dataset.{ Dataset, DatasetStatus }
import org.mongodb.scala.MongoDatabase

import scala.util.Success

class DatasetDaoSpec extends BaseSpec {

  val dataset = Dataset(
    name = "name",
    created = Instant.now(),
    updated = Instant.now(),
    ownerId = UUID.randomUUID,
    status = DatasetStatus.Importing,
    description = None,
    basePath = "/dataset/"
  )

  "DatasetDao" should {
    val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
    val dao = new DatasetDao(mockedMongoDatabase)

    "convert dataset to document and back" in {
      val document = dao.entityToDocument(dataset)
      val datasetEntity = dao.documentToEntity(document)
      datasetEntity shouldBe Success(dataset)
    }
  }

}
