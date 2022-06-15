package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase

import scala.concurrent.{ ExecutionContext, Future }

object CreateInitialCollections extends MongoMigration(
  "Create initial collections",
  LocalDate.of(2018, Month.SEPTEMBER, 12)
) {

  override def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    import MongoMigration.createCollection
    for {
      _ <- createCollection("sharedResources")
      _ <- createCollection("CVFeatureExtractors")
      _ <- createCollection("CVModels")
      _ <- createCollection("CVPredictions")
      _ <- createCollection("albums")
      _ <- createCollection("pictures")
      _ <- createCollection("onlineJobs")
      _ <- createCollection("processes")
      _ <- createCollection("tables")
      _ <- createCollection("tabularModels")
      _ <- createCollection("tabularPredictions")
    } yield ()

  }

}
