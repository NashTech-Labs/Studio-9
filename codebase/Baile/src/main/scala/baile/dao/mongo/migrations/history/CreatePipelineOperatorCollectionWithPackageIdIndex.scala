package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import baile.dao.mongo.migrations.MongoMigration.createCollection
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.mutable.Document

import scala.concurrent.{ ExecutionContext, Future }

object CreatePipelineOperatorCollectionWithPackageIdIndex extends MongoMigration(
  "Create PiplelineOperator collection and index on packageId",
  LocalDate.of(2019, Month.MARCH, 6)
) {

  override def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    for {
      _ <- createCollection("PipelineOperators")
      packages = db.getCollection("PipelineOperators")
      _ <- packages.createIndex(Document {
        "packageId" -> 1
      }).toFuture()
    } yield ()
  }

}
