package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object UpdateCVFeatureExtractorsArchitecture extends MongoMigration(
  "Update CVFeatureExtractors architecture",
  date = LocalDate.of(2018, Month.NOVEMBER, 8)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val cvFeatureExtractors = db.getCollection("CVFeatureExtractors")
    cvFeatureExtractors.updateMany(
      and(exists("architecture"), equal("architecture", "VGG_16")),
      set("architecture", "VGG_16_D")
    ).toFuture().map(_ => ())
  }

}
