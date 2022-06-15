package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.mutable.Document
import org.mongodb.scala.model.Updates.{ rename, _ }
import org.mongodb.scala.model.Filters._

import scala.concurrent.{ ExecutionContext, Future }

object UpdateCVModelsType extends MongoMigration(
  "Update CVModel with model type, remove labelMode",
  date = LocalDate.of(2018, Month.NOVEMBER, 8)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val cvModels = db.getCollection("CVModels")
    for {
      _ <- cvModels.updateMany(Document.empty, rename("type", "localizationMode")).toFuture
      _ <- cvModels.updateMany(
        and(exists("labelMode"), equal("labelMode", "CLASSIFICATION")),
        set("type", Document("labelMode" -> "CLASSIFICATION", "name" -> "FCN_2LAYER"))
      ).toFuture()
      _ <- cvModels.updateMany(
        and(exists("labelMode"), equal("labelMode", "LOCALIZATION")),
        set("type", Document("labelMode" -> "LOCALIZATION", "name" -> "RFBNET"))
      ).toFuture()
      _ <- cvModels.updateMany(
        Document.empty,
        unset("labelMode")
      ).toFuture()
    } yield ()
  }

}
