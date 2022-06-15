package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{ and, equal }
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object TurnTableStatisticStatusFromPendingToErrorIfProcessDoesNotExist extends MongoMigration(
  "Turn tableStatisticsStatus from PENDING to ERROR if process does not exist",
  date = LocalDate.of(2019, Month.AUGUST, 16)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val tables = db.getCollection("tables")
    val processes = db.getCollection("processes")

    val result = for {
      table <- tables.find(
        equal("tableStatisticsStatus", "PENDING")
      )
      processesCount <- processes.countDocuments(
        and(
          equal("targetType", "TABLE"),
          equal("targetId", table.getObjectId("_id").toString)
        )
      )
      if processesCount == 0
      _ <- tables.updateOne(
        Document("_id" -> table.getObjectId("_id")),
        set("tableStatisticsStatus", "ERROR")
      )
    } yield ()

    result.toFuture().map(_ => ())
  }

}
