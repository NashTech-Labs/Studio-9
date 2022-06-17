package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.mutable.Document

import scala.concurrent.{ ExecutionContext, Future }

object CreateOwnerIdIndexForProject extends MongoMigration(
  "Create OwnerId index for Project",
  date = LocalDate.of(2018, Month.DECEMBER, 3)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val projects = db.getCollection("projects")
    projects.createIndex(Document {
      "ownerId" -> 1
    }).toFuture.map(_ => ())
  }

}
