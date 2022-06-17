package baile.dao.project

import java.time.Instant
import java.util.UUID

import baile.BaseItSpec
import baile.dao.mongo.DockerizedMongoDB
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.project.{ ProjectAssetReference, Project }

class ProjectDaoItSpec extends BaseItSpec with DockerizedMongoDB {

  private lazy val dao = new ProjectDao(database)

  private val timeNow = Instant.now()
  private val uuid = UUID.randomUUID
  private val projectEntity = Project(
    name = "name",
    created = timeNow,
    updated = timeNow,
    ownerId = uuid,
    folders = Seq.empty,
    assets = Seq(ProjectAssetReference(AssetReference("cvModelId", AssetType.CvModel), None))
  )

  "dao.addAsset" should {

    "should be able to add asset" in {
      whenReady {
        for {
          id <- dao.create(projectEntity)
          updatedResult <- dao.addAsset(id, ProjectAssetReference(AssetReference("albumId", AssetType.Album), None))
        } yield updatedResult
      } { response =>
        val expectedOutput = projectEntity.copy(
          assets = projectEntity.assets :+ ProjectAssetReference(AssetReference("albumId", AssetType.Album), None)
        )
        response.get.entity shouldBe expectedOutput
      }
    }

    "should not be able to add asset when related project does not exist" in {
      whenReady {
        dao.addAsset(
          "5b580d340ed19141c2cc2400", ProjectAssetReference(AssetReference("albumId", AssetType.Album), None)
        )
      } { response =>
        response shouldBe None
      }
    }
  }

  "dao.removeAsset" should {

    "should be able to remove asset" in {
      whenReady {
        for {
          id <- dao.create(projectEntity)
          updatedResult <- dao.removeAsset(
            id, AssetReference("cvModelId", AssetType.CvModel)
          )
        } yield updatedResult
      } { response =>
        val expectedOutput = projectEntity.copy(assets = Seq())
        response.get.entity shouldBe expectedOutput
      }
    }

    "should not be able to remove asset when related project does not exist" in {
      whenReady {
        dao.removeAsset("5b580d340ed19141c2cc2400",
          AssetReference("cvModelId", AssetType.CvModel)
        )
      } { response =>
        response shouldBe None
      }
    }
  }

}
