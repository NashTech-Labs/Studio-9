package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import baile.dao.mongo.migrations.MongoMigration.createCollection
import org.mongodb.scala.MongoDatabase

import scala.concurrent.{ ExecutionContext, Future }

object CreateExperimentsCollection extends MongoMigration(
  "Create Experiments Collection",
  date = LocalDate.of(2019, Month.MARCH, 20)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    for {
      _ <- createCollection("experiments")
    } yield ()

  }

}
