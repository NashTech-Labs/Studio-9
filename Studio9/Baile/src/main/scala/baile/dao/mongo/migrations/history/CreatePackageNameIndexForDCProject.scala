package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.bson.BsonType
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.mutable.Document
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.model.Filters._

import scala.concurrent.{ ExecutionContext, Future }

object CreatePackageNameIndexForDCProject extends MongoMigration(
  "Create packageName index for DCProject",
  date = LocalDate.of(2019, Month.FEBRUARY, 21)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val projects = db.getCollection("DCProjects")
    projects.createIndex(
      Document {
        "packageName" -> 1
      },
      IndexOptions()
        .unique(true)
        .partialFilterExpression(`type`("packageName", BsonType.STRING))
    ).toFuture.map(_ => ())
  }

}
