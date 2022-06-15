package baile.services.pipeline.category

import java.util.UUID

import baile.ExtendedBaseSpec
import baile.dao.pipeline.category.CategoryDao
import baile.daocommons.WithId
import baile.domain.pipeline.category.Category

class CategoryServiceSpec extends ExtendedBaseSpec {

  trait Setup {
    val dao: CategoryDao = mock[CategoryDao]
    val service: CategoryService = new CategoryService(dao)
    val categories: Seq[Category] = Seq(
      Category("asset-loader", "Asset Loader", "glyp-icon-1"),
      Category("input-output", "Input/Output", "glyp-icon-2")
    )
    val categoriesWithId: Seq[WithId[Category]] =
      categories.map(category => WithId(category, UUID.randomUUID().toString))
  }

  "CategoryService#listAll" should {

    "return list of all categories" in new Setup {
      dao.listAll(*) shouldReturn future(categoriesWithId)
      whenReady(service.listAll) { categories =>
        categories shouldBe categories
      }
    }

  }

}
