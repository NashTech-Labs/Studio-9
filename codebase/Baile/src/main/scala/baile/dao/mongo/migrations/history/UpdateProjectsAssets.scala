package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import baile.dao.mongo.BsonHelpers._
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.bson.collection.immutable.Document

import scala.concurrent.{ ExecutionContext, Future }

object UpdateProjectsAssets extends MongoMigration(
  "Update projects assets",
  date = LocalDate.of(2018, Month.NOVEMBER, 12)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val projects = db.getCollection("projects")
    projects
      .find()
      .flatMap { project =>
        val oldAssets = project.getMandatory[BsonArray]("assets")
        val newAssets = oldAssets.map { oldAsset =>
          Document(
            "assetReference" -> oldAsset,
            "folderId" -> None
          )
        }
        projects.updateOne(
          Document("_id" -> project.getObjectId("_id")),
          set("assets", newAssets)
        )
      }
      .toFuture
      .map(_ => ())
  }

}
