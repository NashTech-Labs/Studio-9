package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.model.Filters.{ equal, exists }
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object SetCVModelFeatureExtractorArchitecture extends MongoMigration(
  "Set CVModel featureExtractorArchitecture",
  date = LocalDate.of(2018, Month.NOVEMBER, 17)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val cvFeatureExtractors = db.getCollection("CVFeatureExtractors")
    val cvModels = db.getCollection("CVModels")
    cvFeatureExtractors.find(exists("architecture")).flatMap { document =>
      val feId = document.getObjectId("_id").toString
      val architecture = document.getString("architecture")

      cvModels.updateMany(
        equal("featureExtractorId", feId),
        set("featureExtractorArchitecture", architecture)
      )
    }.toFuture().map(_ => ())
  }

}
