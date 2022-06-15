package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import baile.dao.mongo.migrations.MongoMigration.createCollection
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.mutable.Document

import scala.concurrent.{ ExecutionContext, Future }

object CreateDCProjectPackagesCollectionWithDCProjectIdIndex extends MongoMigration(
  "Create DCProjectPackages collection and index on dcProjectId",
  LocalDate.of(2019, Month.FEBRUARY, 26)
) {

  override def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    for {
      _ <- createCollection("DCProjectPackages")
      packages = db.getCollection("DCProjectPackages")
      _ <- packages.createIndex(Document {
        "dcProjectId" -> 1
      }).toFuture()
    } yield ()
  }

}
