package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.bson.types.ObjectId
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonDocument, BsonNull, BsonString }
import org.mongodb.scala.model.Updates._
import baile.dao.mongo.BsonHelpers._
import org.bson.BsonType
import org.mongodb.scala.model.Filters

import scala.concurrent.{ ExecutionContext, Future }

object MoveCVModelsFieldsToExperiments extends MongoMigration(
  "Update CVModel by moving experiment related fields to a new experiment",
  date = LocalDate.of(2019, Month.MARCH, 20)
) {

  // scalastyle:off method.length
  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val cvModels = db.getCollection("CVModels")
    val experiments = db.getCollection("experiments")

    def convertStatus(modelStatus: BsonString): BsonString = {
      BsonString(
        modelStatus.getValue match {
          case "SAVING" | "TRAINING" | "PENDING" | "PREDICTING" => "RUNNING"
          case "ACTIVE" => "COMPLETED"
          case "ERROR" => "ERROR"
          case "CANCELLED" => "CANCELLED"
        }
      )
    }

    def buildFEParamsDocument(modelDocument: Document): Document =
      modelDocument.get[BsonString]("featureExtractorId") match {
        case Some(featureExtractorId) => BsonDocument(
          "feParamType" -> BsonString("feIdAndTuneParam"),
          "featureExtractorModelId" -> featureExtractorId,
          "tuneFeatureExtractor" -> BsonBoolean(false)
        )
        case _ => BsonDocument(
          "feParamType" -> BsonString("feArchitecture"),
          "featureExtractorArchitecture" -> modelDocument.get[BsonString]("featureExtractorArchitecture").get,
          "pipelineParams" -> modelDocument.getChildMandatory("featureExtractorParams")
        )
      }

    val result = for {
      modelDocument <- cvModels.find(
        Filters.`type`("inputAlbumId", BsonType.STRING)
      )
      experimentId = new ObjectId
      _ <- experiments.insertOne(
        Document(
          "_id" -> experimentId,
          "ownerId" -> modelDocument.getMandatory[BsonString]("ownerId"),
          "name" -> modelDocument.getMandatory[BsonString]("name"),
          "created" -> modelDocument.getMandatory[BsonString]("created"),
          "updated" -> modelDocument.getMandatory[BsonString]("updated"),
          "status" -> convertStatus(modelDocument.getMandatory[BsonString]("status")),
          "pipeline" -> BsonDocument(
            "_type" -> BsonString("CVTLTrainPipeline"),
            "stepOne" -> BsonDocument(
              "feParams" -> buildFEParamsDocument(modelDocument),
              "modelType" -> modelDocument.getChildMandatory("type"),
              "modelParams" -> modelDocument.getChildMandatory("params"),
              "inputAlbumId" -> modelDocument.getMandatory[BsonString]("inputAlbumId"),
              "testInputAlbumId" -> modelDocument.get[BsonString]("testInputAlbumId"),
              "automatedAugmentationParams" ->
                modelDocument.get[BsonArray]("augmentationSummary").map(x => BsonArray(x.map {
                  _.asDocument.get("augmentationParams")
                }))
            ),
            "stepTwo" -> BsonNull()
          ),
          "result" -> BsonDocument(
            "_type" -> BsonString("CVTLTrainResult"),
            "stepOne" -> BsonDocument(
              "modelId" -> BsonString(modelDocument.getObjectId("_id").toString),
              "outputAlbumId" -> modelDocument.get[BsonString]("outputAlbumId"),
              "testOutputAlbumId" -> modelDocument.get[BsonString]("testOutputAlbumId"),
              "autoAugmentationSampleAlbumId" -> modelDocument.get[BsonString]("autoAugmentationSampleAlbumId"),
              "summary" -> modelDocument.get[BsonDocument]("summary"),
              "testSummary" -> modelDocument.get[BsonDocument]("testSummary"),
              "augmentationSummary" -> modelDocument.get[BsonArray]("augmentationSummary"),
              "trainTimeSpentSummary" -> modelDocument.get[BsonDocument]("trainTimeSpentSummary"),
              "evaluateTimeSpentSummary" -> modelDocument.get[BsonDocument]("evaluateTimeSpentSummary"),
              "probabilityPredictionTableId" -> modelDocument.get[BsonString]("probabilityPredictionTableId"),
              "testProbabilityPredictionTableId" -> modelDocument.get[BsonString]("testProbabilityPredictionTableId")
            ),
            "stepTwo" -> BsonNull()
          ),
          "description" -> modelDocument.get[BsonString]("description")
        ))
      classNames = modelDocument.getChild("summary").map(_.getMandatory[BsonArray]("labels"))
      _ <- cvModels.updateOne(
        Document("_id" -> modelDocument.getObjectId("_id")),
        combine(
          set("experimentId", experimentId.toString),
          set("inLibrary", BsonBoolean(true)),
          set("classNames", classNames.getOrElse(BsonNull())),
          unset("labelMode"),
          unset("inputAlbumId"),
          unset("outputAlbumId"),
          unset("autoAugmentationSampleAlbumId"),
          unset("localizationMode"),
          unset("testInputAlbumId"),
          unset("testOutputAlbumId"),
          unset("summary"),
          unset("testSummary"),
          unset("augmentationSummary"),
          unset("trainTimeSpentSummary"),
          unset("evaluateTimeSpentSummary"),
          unset("featureExtractorParams"),
          unset("params"),
          unset("probabilityPredictionTableId"),
          unset("testProbabilityPredictionTableId")
        )
      )
    } yield ()

    result.toFuture().map(_ => ())
  }
  // scalastyle:on method.length
}
