package baile.dao.dcproject

import java.time.Instant
import java.util.UUID

import baile.BaseSpec
import baile.domain.common.Version
import baile.domain.dcproject.{ DCProject, DCProjectStatus }
import org.mongodb.scala.MongoDatabase

import scala.util.Success

class DCProjectDaoSpec extends BaseSpec {

  val dcProjectSample = DCProject(
    name = "name",
    created = Instant.now(),
    updated = Instant.now(),
    ownerId = UUID.randomUUID,
    status = DCProjectStatus.Idle,
    description = None,
    basePath = "/project1/",
    packageName = Some("packageName"),
    latestPackageVersion = Some(Version(1, 0, 0, None))
  )

  "DCProjectDao" should {
    val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
    val dao = new DCProjectDao(mockedMongoDatabase)

    "convert project to document and back" in {
      val document = dao.entityToDocument(dcProjectSample)
      val projectEntity = dao.documentToEntity(document)
      projectEntity shouldBe Success(dcProjectSample)
    }
  }

}
