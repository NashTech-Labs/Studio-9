package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.model.Filters.{ and, equal, exists }
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object UpdateHandlerNameForDCProjectInExistingProcesses extends MongoMigration(
  "Update Process handler name to DCProjectBuildResultHandler from DCProjectPublishResultHandler",
  date = LocalDate.of(2019, Month.OCTOBER, 29)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val processes = db.getCollection("processes")
    processes.updateMany(
      and(
        exists("onComplete.handlerClassName"),
        equal("onComplete.handlerClassName", "baile.services.dcproject.DCProjectPublishResultHandler")
      ),
      set("onComplete.handlerClassName", "baile.services.dcproject.DCProjectBuildResultHandler")
    ).toFuture().map(_ => ())

  }

}
