package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.model.Filters.{ exists, not }
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object UpdateProjectsWithFolders extends MongoMigration(
  "Update Projects with folders",
  date = LocalDate.of(2018, Month.NOVEMBER, 20)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val projects = db.getCollection("projects")
    projects.updateMany(
      not(exists("folders")),
      set("folders", BsonArray())
    ).toFuture().map(_ => ())
  }
}
