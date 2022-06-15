package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object SetAuxiliaryOnCompleteForProcesses extends MongoMigration(
  "Set auxiliaryOnComplete field for processes",
  date = LocalDate.of(2019, Month.JANUARY, 24)
) {
  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val processes = db.getCollection("processes")
    processes.updateMany(
      Document(),
      set("auxiliaryOnComplete", BsonArray())
    ).toFuture.map(_ => ())
  }
}
