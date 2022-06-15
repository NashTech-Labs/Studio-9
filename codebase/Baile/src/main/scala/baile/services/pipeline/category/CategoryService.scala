package baile.services.pipeline.category

import akka.event.LoggingAdapter
import baile.dao.pipeline.category.CategoryDao
import baile.daocommons.filters.TrueFilter
import baile.domain.pipeline.category.Category

import scala.concurrent.{ ExecutionContext, Future }

class CategoryService(dao: CategoryDao)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) {

  def listAll: Future[Seq[Category]] =
    for {
      categories <- dao.listAll(TrueFilter)
    } yield categories.map(_.entity)

}
