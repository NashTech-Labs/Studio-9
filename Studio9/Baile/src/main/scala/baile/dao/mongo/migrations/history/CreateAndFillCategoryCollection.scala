package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import baile.dao.mongo.migrations.MongoMigration.createCollection
import org.bson.types.ObjectId
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.BsonString
import org.mongodb.scala.bson.collection.immutable.Document

import scala.concurrent.{ ExecutionContext, Future }

object CreateAndFillCategoryCollection extends MongoMigration(
  "Create and Fill Categories Collection",
  date = LocalDate.of(2019, Month.AUGUST, 22)
) {

  // scalastyle:off method.length
  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {

    val ioIcon = "io"
    val transformerIcon = "transformer"
    val detectorIcon = "detector"
    val classifierIcon = "classifier"
    val architectureIcon = "architecture"
    val dataAugmentationIcon = "data-augmentation"
    val otherIcon = "other"

    val selectorCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("SELECTOR"),
      "name" -> BsonString("Selector"),
      "icon" -> BsonString(ioIcon)
    )
    val saverCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("SAVER"),
      "name" -> BsonString("Saver"),
      "icon" -> BsonString(ioIcon)
    )
    val tableTransformerCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("TABLE_TRANSFORMER"),
      "name" -> BsonString("Table Transformer"),
      "icon" -> BsonString(transformerIcon)
    )
    val temporalTransformerCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("TEMPORAL_TRANSFORMER"),
      "name" -> BsonString("Temporal Transformer"),
      "icon" -> BsonString(transformerIcon)
    )
    val albumTransformerCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("ALBUM_TRANSFORMER"),
      "name" -> BsonString("Album Transformer"),
      "icon" -> BsonString(transformerIcon)
    )
    val detectorPrimitiveCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("DETECTOR_PRIMITIVE"),
      "name" -> BsonString("Detector Primitive"),
      "icon" -> BsonString(detectorIcon)
    )
    val classifierPrimitiveCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("CLASSIFIER_PRIMITIVE"),
      "name" -> BsonString("Classifier Primitive"),
      "icon" -> BsonString(classifierIcon)
    )
    val learnerCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("LEARNER"),
      "name" -> BsonString("Learner"),
      "icon" -> BsonString(architectureIcon)
    )
    val predictorCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("PREDICTOR"),
      "name" -> BsonString("Predictor"),
      "icon" -> BsonString(architectureIcon)
    )
    val sqlFunctionCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("SQL_FUNCTION"),
      "name" -> BsonString("SQL Function"),
      "icon" -> BsonString(transformerIcon)
    )
    val pandasFunctionCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("PANDAS_FUNCTION"),
      "name" -> BsonString("PANDAS Function"),
      "icon" -> BsonString(transformerIcon)
    )
    val scikitFunctionCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("SCIKIT_FUNCTION"),
      "name" -> BsonString("Scikit Function"),
      "icon" -> BsonString(transformerIcon)
    )
    val nipypeFunctionCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("NIPYPE_FUNCTION"),
      "name" -> BsonString("NIPYPE Function"),
      "icon" -> BsonString(transformerIcon)
    )
    val pipelineOperatorCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("PIPELINE_OPERATOR"),
      "name" -> BsonString("Pipeline Operator"),
      "icon" -> BsonString(dataAugmentationIcon)
    )
    val otherCategory = Document(
      "_id" -> new ObjectId,
      "id" -> BsonString("OTHER"),
      "name" -> BsonString("Other"),
      "icon" -> BsonString(otherIcon)
    )

    val documents: Seq[Document] = Seq(
      selectorCategory,
      saverCategory,
      tableTransformerCategory,
      temporalTransformerCategory,
      albumTransformerCategory,
      detectorPrimitiveCategory,
      classifierPrimitiveCategory,
      learnerCategory,
      predictorCategory,
      sqlFunctionCategory,
      pandasFunctionCategory,
      scikitFunctionCategory,
      nipypeFunctionCategory,
      pipelineOperatorCategory,
      otherCategory
    )

    for {
      _ <- createCollection("categories")
      categories = db.getCollection("categories")
      _ <- categories.insertMany(documents).toFuture
    } yield ()

  }
  // scalastyle:off method.length

}
