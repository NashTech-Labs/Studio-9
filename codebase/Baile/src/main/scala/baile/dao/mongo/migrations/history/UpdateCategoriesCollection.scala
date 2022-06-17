package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.bson.types.ObjectId
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.BsonString
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.bson.collection.immutable.Document

import scala.concurrent.{ ExecutionContext, Future }

object UpdateCategoriesCollection extends MongoMigration(
  "Update Categories Collection",
  date = LocalDate.of(2019, Month.OCTOBER, 10)
) {

  // scalastyle:off method.length
  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {

    val classifierCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("CLASSIFIER"),
      "name" -> BsonString("Classifier"),
      "icon" -> BsonString("classifier")
    )
    val dataPreparerCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("DATA_PREPARER"),
      "name" -> BsonString("Data Preparer"),
      "icon" -> BsonString("data-preparer")
    )
    val detectorCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("DETECTOR"),
      "name" -> BsonString("Detector"),
      "icon" -> BsonString("detector")
    )
    val featureExtractorCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("FEATURE_EXTRACTOR"),
      "name" -> BsonString("Feature Extractor"),
      "icon" -> BsonString("feature-extractor")
    )
    val featureTransformerCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("FEATURE_TRANSFORMER"),
      "name" -> BsonString("Feature Transformer"),
      "icon" -> BsonString("feature-transformer")
    )
    val learnerCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("LEARNER"),
      "name" -> BsonString("Learner"),
      "icon" -> BsonString("learner")
    )
    val metricsCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("METRICS"),
      "name" -> BsonString("Metrics"),
      "icon" -> BsonString("metrics")
    )
    val predictorCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("PREDICTOR"),
      "name" -> BsonString("Predictor"),
      "icon" -> BsonString("predictor")
    )
    val saverCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("SAVER"),
      "name" -> BsonString("Saver"),
      "icon" -> BsonString("saver")
    )
    val selectorCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("SELECTOR"),
      "name" -> BsonString("Selector"),
      "icon" -> BsonString("selector")
    )
    val transformerCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("TRANSFORMER"),
      "name" -> BsonString("Transformer"),
      "icon" -> BsonString("transformer")
    )
    val decoderCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("DECODER"),
      "name" -> BsonString("Decoder"),
      "icon" -> BsonString("decoder")
    )
    val otherCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("OTHER"),
      "name" -> BsonString("Other"),
      "icon" -> BsonString("other")
    )

    val documents = Seq(
      classifierCategory,
      dataPreparerCategory,
      detectorCategory,
      featureExtractorCategory,
      featureTransformerCategory,
      learnerCategory,
      metricsCategory,
      predictorCategory,
      saverCategory,
      selectorCategory,
      transformerCategory,
      decoderCategory,
      otherCategory
    )

    val categories = db.getCollection("categories")
    val pipelineOperators = db.getCollection("pipelineOperators")
    for {
      _ <- categories.deleteMany(Document.empty).toFuture()
      _ <- categories.insertMany(documents).toFuture()
      _ <- pipelineOperators.updateMany(
        and(exists("category"), equal("category", "DETECTOR_PRIMITIVE")),
        set("category", "DETECTOR")
      ).toFuture()
      _ <- pipelineOperators.updateMany(
        and(exists("category"), equal("category", "CLASSIFIER_PRIMITIVE")),
        set("category", "CLASSIFIER")
      ).toFuture()
      _ <- pipelineOperators.updateMany(
        and(exists("category"), or(
          equal("category", "TABLE_TRANSFORMER"),
          equal("category", "TEMPORAL_TRANSFORMER"),
          equal("category", "ALBUM_TRANSFORMER")
        )),
        set("category", "TRANSFORMER")
      ).toFuture()
      _ <- pipelineOperators.updateMany(
        and(exists("category"), and(
          notEqual("category", "DETECTOR"),
          notEqual("category", "CLASSIFIER"),
          notEqual("category", "TRANSFORMER"),
          notEqual("category", "LEARNER"),
          notEqual("category", "PREDICTOR"),
          notEqual("category", "SAVER"),
          notEqual("category", "SELECTOR"),
          notEqual("category", "OTHER")
        )),
        set("category", "OTHER")
      ).toFuture()
    } yield ()

  }
  // scalastyle:off method.length

}
