package baile.dao.onlinejob

import baile.BaseSpec
import baile.dao.onlinejob.util.TestData._
import org.mongodb.scala.{ Document, MongoDatabase }
import org.scalatest.PrivateMethodTester
import org.scalatest.prop.TableDrivenPropertyChecks

class OnlineJobDaoSpec extends BaseSpec with PrivateMethodTester with TableDrivenPropertyChecks {

  val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
  val onlineJobDao = new OnlineJobDao(mockedMongoDatabase)

  val testData = Table(
    ("clue", "data1", "data2"),
    ("data is correct", OnlineJobDocument, OnlineJobEntity)
  )

  "OnlineJobDao" when {
    forAll(testData) { (clue, data1, data2) =>
      s"$clue" should {
        "be able to convert document into entity" in {
          val action = onlineJobDao.documentToEntity(data1)
          action.success.value shouldBe data2
        }

        "be able to convert entity into document" in {
          val action = onlineJobDao.entityToDocument(data2)
          action shouldBe data1
        }

      }
    }

    "document is empty" should {
      "not be able to convert document into entity" in {
        val result = onlineJobDao.documentToEntity(Document())
        assert(result.isFailure)
      }
    }
  }

}
