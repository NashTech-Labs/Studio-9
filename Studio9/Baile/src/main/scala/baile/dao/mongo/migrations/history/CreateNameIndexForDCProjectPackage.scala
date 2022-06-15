package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.immutable.Document

import scala.concurrent.{ ExecutionContext, Future }

object CreateNameIndexForDCProjectPackage extends MongoMigration(
  "Create name index for DCProjectPackage",
  date = LocalDate.of(2019, Month.APRIL, 25)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val projectPackages = db.getCollection("DCProjectPackages")
    projectPackages
      .createIndex(
        Document {
          "name" -> 1
        }
      )
      .toFuture
      .map(_ => ())
  }

}
