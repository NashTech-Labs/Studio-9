package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.mutable.Document

import scala.concurrent.{ ExecutionContext, Future }

object CreateDCProjectIdIndexForDCProjectSession extends MongoMigration(
  "Create DCProjectId index for DCProjectSession",
  date = LocalDate.of(2019, Month.FEBRUARY, 18)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val sessions = db.getCollection("DCProjectsSession")
    sessions.createIndex(Document {
      "dcProjectId" -> 1
    }, IndexOptions().unique(true)).toFuture.map(_ => ())
  }

}
