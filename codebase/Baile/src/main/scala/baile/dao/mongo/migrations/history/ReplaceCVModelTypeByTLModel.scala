package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.{ BsonNull, Document }
import org.mongodb.scala.model.Filters.{ and, equal, exists, not }
import org.mongodb.scala.model.Updates.{ combine, rename, set }

import scala.concurrent.{ ExecutionContext, Future }

object ReplaceCVModelTypeByTLModel extends MongoMigration(
  "Replace CVModelType by CVModelType.TL",
  date = LocalDate.of(2019, Month.JUNE, 24)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val cvModels = db.getCollection("CVModels")
    val experiments = db.getCollection("experiments")

    for {
      // migrate CVModels
      _ <- cvModels.updateMany(Document.empty, rename("type.type", "type.tlType")).toFuture
      _ <- cvModels.updateMany(
        Document.empty,
        combine(
          set("type.type", "TL"),
          rename("featureExtractorArchitecture", "type.featureExtractorArchitecture")
        )
      ).toFuture

      // migrate experiments
      isCVTLTrainPipeline = equal("pipeline._type", "CVTLTrainPipeline")
      _ <- experiments.updateMany(
        isCVTLTrainPipeline,
        rename("pipeline.stepOne.modelType.type", "pipeline.stepOne.modelType.tlType")
      ).toFuture
      _ <- experiments.updateMany(
        and(
          isCVTLTrainPipeline,
          exists("pipeline.stepTwo"),
          not(
            equal("pipeline.stepTwo", BsonNull())
          )
        ),
        rename("pipeline.stepTwo.modelType.type", "pipeline.stepTwo.modelType.tlType")
      ).toFuture
    } yield ()
  }
}
