package baile.dao.asset.sharing

import baile.BaseSpec
import baile.dao.asset.sharing.util.TestData
import baile.dao.asset.sharing.util.TestData._
import org.mongodb.scala.{ Document, MongoDatabase }
import org.scalatest.PrivateMethodTester
import org.scalatest.prop.TableDrivenPropertyChecks

class SharedResourceDaoSpec extends BaseSpec with PrivateMethodTester with TableDrivenPropertyChecks {

  val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
  val sharedResourceDao = new SharedResourceDao(mockedMongoDatabase)

  val testData = Table(
    ("clue", "data1", "data2"),
    ("asset is Table", SharedResourceDocument, SharedResourceEntity),
    ("optional fields does not have value", DocumentWithNull, EntityWithNone)
  )

  "SharedResourceDao" when {
    forAll(testData) { (clue, data1, data2) =>
      s"$clue" should {
        "be able to convert document into entity" in {
          val action = sharedResourceDao.documentToEntity(data1)
          action.success.value.ownerId shouldBe data2.ownerId
        }

        "be able to convert entity into document" in {
          val action = sharedResourceDao.entityToDocument(data2)
          action.getString("ownerId") shouldBe TestData.OwnerID.toString
        }

      }
    }

    "document is empty" should {
      "not be able to convert document into entity" in {
        val result = sharedResourceDao.documentToEntity(Document())
        assert(result.isFailure)
      }
    }
  }

}
