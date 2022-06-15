package baile.dao.images

import baile.BaseSpec
import baile.dao.images.util.AlbumTestData._
import org.mongodb.scala.{ Document, MongoDatabase }
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.util.Success

class AlbumDaoSpec extends BaseSpec with TableDrivenPropertyChecks {

  val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
  val albumDao: AlbumDao = new AlbumDao(mockedMongoDatabase)

  val testData = Table(
    ("clue", "data1", "data2"),
    ("status is active, type is TrainResults and labelMode is Classification", AlbumEntity, AlbumDoc),
    ("status is Failing, type is Derived and labelMode is Localization", AlbumWithFDL, AlbumWithFDLDoc),
    ("status is Uploading, type is Source and labelMode is Classification", AlbumWithUSC, AlbumWithUSCDoc),
    ("status is Saving, type is TrainResults and labelMode is Classification", AlbumWithSTC, AlbumWithSTCDoc),
    ("uploadJobId and video are None", AlbumEntityWithNone, AlbumDocWithNone)
  )

  "AlbumDao" when {
    forAll(testData) { (clue, data1, data2) =>
      s"$clue" should {
        "be able to convert entity into document and back" in {
          val document = albumDao.entityToDocument(data1)
          val newEntity = albumDao.documentToEntity(document)
          newEntity shouldBe Success(data1)
        }

        "be able to convert entity into document" in {
          val action = albumDao.entityToDocument(data1)
          action shouldBe data2
        }

        "be able to convert document into entity" in {
          val action = albumDao.documentToEntity(data2)
          action.success.value shouldBe data1
        }
      }
    }

    "document is empty" should {
      "not be able to convert document into entity" in {
        val result = albumDao.documentToEntity(Document())
        assert(result.isFailure)
      }
    }
  }

}
