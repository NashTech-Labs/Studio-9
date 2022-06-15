package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Updates._

import scala.concurrent.{ ExecutionContext, Future }

object AddIsPublishedFieldInDCProjectPackage extends MongoMigration(
  "Add isPublished field in existing packages",
  date = LocalDate.of(2019, Month.SEPTEMBER, 30)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    db.getCollection("DCProjectPackages")
      .updateMany(
        Document.empty,
        set("isPublished", true)
      )
      .toFuture
      .map(_ => ())

  }

}
