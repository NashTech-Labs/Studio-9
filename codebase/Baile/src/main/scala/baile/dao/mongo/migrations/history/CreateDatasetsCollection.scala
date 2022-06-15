package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase

import scala.concurrent.{ ExecutionContext, Future }

object CreateDatasetsCollection extends MongoMigration(
  "Create datasets collection",
  LocalDate.of(2019, Month.JULY, 31)
) {

  override def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    import MongoMigration.createCollection
    for {
      _ <- createCollection("datasets")
    } yield ()

  }

}
