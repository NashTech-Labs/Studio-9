package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.mutable.Document
import org.mongodb.scala.model.Updates._

import scala.concurrent.{ ExecutionContext, Future }

object RenameCVCollectionsFields extends MongoMigration(
  "Rename CVModels, CVPredictions and CVFeatureExtractors fields",
  date = LocalDate.of(2018, Month.SEPTEMBER, 19)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val cvModels = db.getCollection("CVModels")
    val cvPredictions = db.getCollection("CVPredictions")
    val cvFeatureExtractors = db.getCollection("CVFeatureExtractors")
    for {
      _ <- cvModels.updateMany(Document.empty, rename("input", "inputAlbumId")).toFuture
      _ <- cvModels.updateMany(Document.empty, rename("output", "outputAlbumId")).toFuture
      _ <- cvModels.updateMany(Document.empty, rename("testInput", "testInputAlbumId")).toFuture
      _ <- cvModels.updateMany(Document.empty, rename("testOutput", "testOutputAlbumId")).toFuture
      _ <- cvModels.updateMany(Document.empty, rename("featureExtractor", "featureExtractorId")).toFuture
      _ <- cvModels.updateMany(Document.empty, rename("modelFilePath", "cortexFilePath")).toFuture
      _ <- cvPredictions.updateMany(Document.empty, rename("input", "inputAlbumId")).toFuture
      _ <- cvPredictions.updateMany(Document.empty, rename("output", "outputAlbumId")).toFuture
      _ <- cvFeatureExtractors.updateMany(Document.empty, rename("input", "inputAlbumId")).toFuture
    } yield ()
  }

}
