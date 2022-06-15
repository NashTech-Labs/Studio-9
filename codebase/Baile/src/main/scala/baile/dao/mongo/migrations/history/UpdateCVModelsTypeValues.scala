package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.model.Filters.{ and, equal, exists }
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object UpdateCVModelsTypeValues extends MongoMigration (
  "Update CVModel types values",
  date = LocalDate.of(2019, Month.JANUARY, 30)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val cvModels = db.getCollection("CVModels")

    for {
      _ <- cvModels.updateMany(
        and(exists("type.name"), equal("type.name", "FCN_1LAYER")),
        set("type.name", "FCN_1")
      ).toFuture()
      _ <- cvModels.updateMany(
        and(exists("type.name"), equal("type.name", "FCN_2LAYER")),
        set("type.name", "FCN_2")
      ).toFuture()
      _ <- cvModels.updateMany(
        and(exists("type.name"), equal("type.name", "FCN_3LAYER")),
        set("type.name", "FCN_3")
      ).toFuture()
      _ <- cvModels.updateMany(
        and(exists("type.name"), equal("type.name", "RFBNET")),
        set("type.name", "RFBNet")
      ).toFuture()
      _ <- cvModels.updateMany(
        and(
          exists("featureExtractorArchitecture"),
          equal("featureExtractorArchitecture", "VGG_16_D")
        ),
        set("featureExtractorArchitecture", "VGG16_RFB")
      ).toFuture()
      _ <- cvModels.updateMany(
        and(
          exists("featureExtractorArchitecture"),
          equal("featureExtractorArchitecture", "VGG_16_C")
        ),
        set("featureExtractorArchitecture", "VGG16")
      ).toFuture()
      _ <- cvModels.updateMany(
        and(
          exists("featureExtractorArchitecture"),
          equal("featureExtractorArchitecture", "STACKED_AUTOENCODER")
        ),
        set("featureExtractorArchitecture", "SCAE")
      ).toFuture()
    } yield ()

  }

}
