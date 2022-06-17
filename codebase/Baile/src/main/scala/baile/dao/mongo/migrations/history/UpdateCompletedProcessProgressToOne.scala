package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.BsonDouble
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates.set

import scala.concurrent.ExecutionContext

object UpdateCompletedProcessProgressToOne extends MongoMigration(
  "Update completed job processes progress to 1.0",
  date = LocalDate.of(2019, Month.OCTOBER, 30)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext) = {
    val processes = db.getCollection("processes")
    processes.updateMany(
      equal("status", "COMPLETED"),
      set("progress", BsonDouble(1.0))
    ).toFuture().map(_ => ())
  }

}
