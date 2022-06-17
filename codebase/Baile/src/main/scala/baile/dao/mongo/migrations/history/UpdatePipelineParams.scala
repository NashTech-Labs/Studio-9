package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }
import baile.dao.mongo.BsonHelpers._

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{ exists, not }
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object UpdatePipelineParams extends MongoMigration(
  "Move pipeline params from CVFeatureExtractor to CVModel or create empty FE pipeline params for CVModel",
  date = LocalDate.of(2019, Month.APRIL, 3)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val cvFeatureExtractors = db.getCollection("CVFeatureExtractors")
    val cvModels = db.getCollection("CVModels")

    val cvModelsUpdateResult = for {
      feDocument <- cvFeatureExtractors.find()
      feParams = feDocument.getChildMandatory("params")
      result <- cvModels.updateOne(
        Document("_id" -> feDocument.getObjectId("_id")),
        set("featureExtractorParams", feParams)
      )
    } yield result

    for {
      _ <- cvModelsUpdateResult.toFuture()
      _ <- cvModels.updateMany(
        not(exists("featureExtractorParams")), set("featureExtractorParams", BsonDocument())
      ).toFuture()
    } yield ()
  }
}
