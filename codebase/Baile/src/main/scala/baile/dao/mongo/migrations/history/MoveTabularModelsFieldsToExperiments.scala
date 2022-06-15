package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.bson.types.ObjectId
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonDocument, BsonNull, BsonObjectId, BsonString }
import org.mongodb.scala.bson.collection.immutable.Document
import baile.dao.mongo.BsonHelpers._
import org.mongodb.scala.model.Updates.{ combine, set, unset }

import scala.concurrent.{ ExecutionContext, Future }

object MoveTabularModelsFieldsToExperiments extends MongoMigration(
  "Update TabularModel by moving experiment related fields to a new experiment",
  date = LocalDate.of(2019, Month.MAY, 10)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val tabularModels = db.getCollection("tabularModels")
    val experiments = db.getCollection("experiments")

    val result = for {
      modelDocument <- tabularModels.find()
      experimentId = new ObjectId
      experimentStatus = modelDocument.getMandatory[BsonString]("status").getValue match {
        case "ACTIVE" => "COMPLETED"
        case "TRAINING" => "RUNNING"
        case "PREDICTING" => "RUNNING"
        case "ERROR" => "ERROR"
        case "CANCELLED" => "CANCELLED"
      }
      _ <- experiments.insertOne(
        Document(
          "_id" -> experimentId,
          "ownerId" -> modelDocument.getMandatory[BsonString]("ownerId"),
          "name" -> modelDocument.getMandatory[BsonString]("name"),
          "created" -> modelDocument.getMandatory[BsonString]("created"),
          "updated" -> modelDocument.getMandatory[BsonString]("updated"),
          "status" -> BsonString(experimentStatus),
          "pipeline" -> BsonDocument(
            "_type" -> BsonString("TabularTrainPipeline"),
            "samplingWeightColumnName" -> modelDocument
              .getChild("samplingWeightColumn")
              .map(_.getMandatory[BsonString]("name")),
            "predictorColumns" -> modelDocument.getMandatory[BsonArray]("predictorColumns"),
            "responseColumn" -> modelDocument.getMandatory[BsonDocument]("responseColumn"),
            "inputTableId" -> modelDocument.getMandatory[BsonString]("inputTableId"),
            "holdOutInputTableId" -> modelDocument.get[BsonString]("holdOutInputTableId"),
            "outOfTimeInputTableId" -> modelDocument.get[BsonString]("outOfTimeInputTableId")
          ),
          "result" -> BsonDocument(
            "_type" -> BsonString("TabularTrainResult"),
            "modelId" -> modelDocument.getMandatory[BsonObjectId]("_id").getValue.toString,
            "outputTableId" -> modelDocument.getMandatory[BsonString]("outputTableId"),
            "holdOutOutputTableId" -> modelDocument.get[BsonString]("holdOutOutputTableId"),
            "outOfTimeOutputTableId" -> modelDocument.get[BsonString]("outOfTimeOutputTableId"),
            "predictedColumnName" -> modelDocument
              .getChildMandatory("predictedColumn")
              .getMandatory[BsonString]("name"),
            "classes" -> modelDocument.get[BsonArray]("classes"),
            "summary" -> modelDocument.getChild("summary"),
            "holdOutSummary" -> modelDocument.getChild("holdOutSummary"),
            "outOfTimeSummary" -> modelDocument.getChild("outOfTimeSummary"),
            "predictorsSummary" -> modelDocument.getMandatory[BsonArray]("predictorsSummary")
          ),
          "description" -> modelDocument.get[BsonString]("description")
        ))
      classNames = modelDocument.get[BsonArray]("classes").map(_.map { elem =>
        Document(elem.asDocument).getMandatory[BsonString]("className")
      })
      cortexModelReference = modelDocument.get[BsonString]("cortexId").map { cortexId =>
        Document(
          "cortexId" -> cortexId,
          "cortexFilePath" -> modelDocument.get[BsonString]("cortexFilePath")
            .getOrElse(throw new RuntimeException(
              s"Not found cortexFilePath for tabular model ${ modelDocument.getObjectId("_id") }" +
                s" while cortex id was present (${ cortexId })"
            )
          )

        )
      }
      _ <- tabularModels.updateOne(
        Document("_id" -> modelDocument.getObjectId("_id")),
        combine(
          set("experimentId", BsonString(experimentId.toString)),
          set("classNames", classNames.getOrElse(BsonNull())),
          set("cortexModelReference", cortexModelReference.getOrElse(BsonNull())),
          set("inLibrary", BsonBoolean(true)),
          unset("samplingWeightColumn"),
          unset("cortexId"),
          unset("cortexFilePath"),
          unset("inputTableId"),
          unset("outputTableId"),
          unset("holdOutInputTableId"),
          unset("holdOutOutputTableId"),
          unset("outOfTimeInputTableId"),
          unset("outOfTimeOutputTableId"),
          unset("summary"),
          unset("holdOutSummary"),
          unset("outOfTimeSummary"),
          unset("predictorsSummary")
        )
      )
    } yield ()

    result.toFuture().map(_ => ())
  }

}
