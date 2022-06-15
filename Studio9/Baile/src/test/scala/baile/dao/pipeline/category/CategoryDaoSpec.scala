package baile.dao.pipeline.category

import baile.ExtendedBaseSpec
import baile.domain.pipeline.category.Category
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.{ Success, Try }

class CategoryDaoSpec extends ExtendedBaseSpec {

  trait Setup {
    val category = Category(
      id = "category",
      name = "Category",
      icon = "icon"
    )
    val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
    val dao: CategoryDao = new CategoryDao(mockedMongoDatabase)
  }

  "CategoryDao" should {

    "convert category to document and back" in new Setup {
      val document: Document = dao.entityToDocument(category)
      val categoryEntity: Try[Category] = dao.documentToEntity(document)
      categoryEntity shouldBe Success(category)
    }

  }

}
