package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase

import scala.concurrent.{ ExecutionContext, Future }

object CreateCVModelTypeAndFEArchitectureCollections extends MongoMigration (
  "Create collections for classifiers, localizers, FEArchitectures and autoEncoders",
  LocalDate.of(2019, Month.JANUARY, 30)
) {

  override def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    import MongoMigration.createCollection
    for {
      _ <- createCollection("classifiers")
      _ <- createCollection("localizers")
      _ <- createCollection("autoEncoders")
      _ <- createCollection("FEArchitectures")
    } yield ()

  }

}
