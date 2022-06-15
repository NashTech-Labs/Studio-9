package baile.dao.project

import baile.BaseSpec
import org.mongodb.scala.MongoDatabase
import baile.services.project.util.TestData.ProjectSample
import scala.util.Success

class ProjectDaoSpec extends BaseSpec {

  "ProjectDao" should {
    val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
    val dao = new ProjectDao(mockedMongoDatabase)

    "convert model to document and back" in {
      val document = dao.entityToDocument(ProjectSample)
      val projectEntity = dao.documentToEntity(document)
      projectEntity shouldBe Success(ProjectSample)
    }
  }

}
