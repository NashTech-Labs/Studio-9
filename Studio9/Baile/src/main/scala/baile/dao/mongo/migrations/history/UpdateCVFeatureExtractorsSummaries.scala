package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.{ BsonArray, BsonNull }
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._

import scala.concurrent.{ ExecutionContext, Future }

object UpdateCVFeatureExtractorsSummaries extends MongoMigration(
  "Update CVFeatureExtractors summaries",
  date = LocalDate.of(2018, Month.OCTOBER, 8)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val cvFeatureExtractors = db.getCollection("CVFeatureExtractors")
    cvFeatureExtractors.updateMany(
      and(
        and(exists("summary"), notEqual("summary", BsonNull())),
        not(exists("summary.labels"))
      ),
      set("summary.labels", BsonArray())
    ).toFuture().map(_ => ())
  }

}
