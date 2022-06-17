package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.model.Filters.{ and, equal, exists }
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object UpdateDCProjectStatus extends MongoMigration(
  "Update DCProject status PUBLISHING to BUILDING",
  date = LocalDate.of(2019, Month.OCTOBER, 18)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val dcProjects = db.getCollection("DCProjects")
    dcProjects.updateMany(
      and(
        exists("status"),
        equal("status", "PUBLISHING")
      ),
      set("status", "BUILDING")
    ).toFuture().map(_ => ())

  }

}
