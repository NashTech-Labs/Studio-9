package baile.dao.dcproject

import java.time.Instant
import java.util.UUID

import baile.ExtendedBaseSpec
import baile.domain.common.Version
import baile.domain.dcproject.DCProjectPackage
import org.mongodb.scala.MongoDatabase
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.util.Success

class DCProjectPackageDaoSpec extends ExtendedBaseSpec with TableDrivenPropertyChecks {

  val dcProjectPackageSamples = Table(
    "sample",
    DCProjectPackage(
      name = "packageName",
      created = Instant.now(),
      ownerId = Some(UUID.randomUUID),
      location = Some("/package/"),
      version = Some(Version(1, 0, 0, None)),
      dcProjectId = Some("projectId"),
      description = Some("package description"),
      isPublished = true
    ),
    DCProjectPackage(
      name = "packageName",
      created = Instant.now(),
      ownerId = Some(UUID.randomUUID),
      location = Some("/package/"),
      version = None,
      dcProjectId = Some("projectId"),
      description = Some("package description"),
      isPublished = false
    )
  )

  "DCProjectPackageDao" should {
    val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
    val dao = new DCProjectPackageDao(mockedMongoDatabase)

    "convert package to document and back" in {
      forAll(dcProjectPackageSamples) { dcProjectPackageSample =>
        val document = dao.entityToDocument(dcProjectPackageSample)
        val packageEntity = dao.documentToEntity(document)
        packageEntity shouldBe Success(dcProjectPackageSample)
      }
    }
  }

}
