package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.model.Filters.{ and, equal, exists }
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object UpdateCVModelsClassifierType extends MongoMigration(
  "Update CVModel classifier type FCN_2LAYER to FCN_1LAYER",
  date = LocalDate.of(2018, Month.NOVEMBER, 15)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val cvModels = db.getCollection("CVModels")
    cvModels.updateMany(
      and(
        exists("type.name"),
        equal("type.name", "FCN_2LAYER")
      ),
      set("type.name", "FCN_1LAYER")
    ).toFuture().map(_ => ())

  }

}
