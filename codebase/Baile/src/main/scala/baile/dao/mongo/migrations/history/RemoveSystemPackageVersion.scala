package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model.Filters._

import scala.concurrent.{ ExecutionContext, Future }

object RemoveSystemPackageVersion extends MongoMigration(
  "Remove version from system package",
  LocalDate.of(2019, Month.JULY, 8)
) {

  override def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {

    val dcProjectPackages = db.getCollection("DCProjectPackages")

    for {
      _ <- dcProjectPackages.updateOne(
        equal("name", "deepcortex-ml-lib"),
        unset("version")
      ).toFuture
    } yield ()
  }

}
