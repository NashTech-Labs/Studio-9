package baile.dao.process

import baile.BaseSpec
import baile.services.process.util.TestData._
import org.mongodb.scala.MongoDatabase

import scala.util.Success

class ProcessDaoSpec extends BaseSpec {
  "ProcessDao" should {
    val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
    val dao: ProcessDao = new ProcessDao(mockedMongoDatabase)

    val process = SampleProcess.entity

    "convert model to document and back" in {
      val document = dao.entityToDocument(process)
      val restoredProcess = dao.documentToEntity(document)

      restoredProcess shouldBe Success(process)
    }
  }
}
