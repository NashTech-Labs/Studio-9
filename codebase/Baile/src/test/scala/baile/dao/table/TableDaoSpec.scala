package baile.dao.table

import baile.BaseSpec
import baile.dao.table.util.TestData._
import org.mongodb.scala.{ Document, MongoDatabase }
import org.scalatest.prop.TableDrivenPropertyChecks

class TableDaoSpec extends BaseSpec with TableDrivenPropertyChecks {

  val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
  val tableDao: TableDao = new TableDao(mockedMongoDatabase)

  val testData = Table(
    ("clue", "data1", "data2"),
    ("status is Saving", TableEntitySavingAndDerived, TableDocumentWithSavingAndDerived),
    ("status is Active", TableEntityWithStatusActive, TableDocumentWithActive),
    ("status is Inactive", TableEntityWithStatusInactive, TableDocumentWithStatusInactive),
    ("status is Error", TableEntityWithStatusError, TableDocumentWithStatusError)
  )

  "TableDao" when {
    forAll(testData) { (clue, data1, data2) =>
      s"$clue" should {
        "be able to convert entity into document" in {
          val action = tableDao.entityToDocument(data1)
          action shouldBe data2
        }

        "be able to convert document into entity" in {
          val action = tableDao.documentToEntity(data2)
          action.success.value shouldBe data1
        }
      }
    }

    "document is empty" should {
      "not be able to convert document into entity" in {
        val result = tableDao.documentToEntity(Document())
        assert(result.isFailure)
      }
    }
  }

}
