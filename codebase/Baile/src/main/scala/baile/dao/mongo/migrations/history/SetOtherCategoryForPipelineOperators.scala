package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object SetOtherCategoryForPipelineOperators extends MongoMigration(
  "Set OTHER category for existing pipeline operators",
  LocalDate.of(2019, Month.AUGUST, 12)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] =
    db.getCollection("pipelineOperators")
      .updateMany(
        Document.empty,
        set("category", "OTHER")
      )
      .toFuture
      .map(_ => ())

}
