package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import baile.dao.mongo.migrations.MongoMigration.createCollection
import org.mongodb.scala.MongoDatabase

import scala.concurrent.{ ExecutionContext, Future }

object CreateDCProjectSessionsCollection extends MongoMigration(
  "Create DCProjectSessions collection",
  LocalDate.of(2019, Month.FEBRUARY, 11)
) {

  override def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    createCollection("DCProjectSessions").map(_ => ())
  }

}
