package baile.dao.cv.prediction

import java.time.Instant
import java.util.UUID

import baile.dao.CommonSerializers
import baile.dao.asset.Filters._
import baile.dao.cv.model.CVModelPipelineSerializer
import baile.dao.cv.prediction.CVPredictionDao._
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.common.ConfusionMatrixCell
import baile.domain.cv.LabelOfInterest
import baile.domain.cv.model.CVModelSummary
import baile.domain.cv.prediction._
import org.mongodb.scala.bson.{ BsonArray, BsonDocument, BsonDouble, BsonInt32, BsonInt64, BsonString }
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

object CVPredictionDao {

  case class StatusIs(status: CVPredictionStatus) extends Filter
  case object Name extends Field
  case object Created extends Field
  case object Updated extends Field

  case class AlbumIdIs(albumId: String) extends Filter
  case class CVModelIdIs(cvModelId: String) extends Filter

}

class CVPredictionDao(protected val database: MongoDatabase) extends MongoEntityDao[CVPrediction] {

  override val collectionName: String = "CVPredictions"

  override protected val fieldMapper: Map[Field, String] =
    Map(
      Name -> "name",
      Created -> "created",
      Updated -> "updated"
    )
  override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
    case OwnerIdIs(ownerId) => Try(MongoFilters.equal("ownerId", ownerId.toString))
    case NameIs(name) => Try(MongoFilters.equal("name", name))
    case InLibraryIs(inLibrary) => Try(MongoFilters.exists("_id", inLibrary))
    case StatusIs(status) => Try(MongoFilters.equal("status", predictionStatusToString(status)))
    // TODO add protection from injections to regex
    case SearchQuery(term) => Try(MongoFilters.regex("name", term, "i"))
    case AlbumIdIs(albumId) => Try(MongoFilters.or(
      MongoFilters.equal("inputAlbumId", albumId),
      MongoFilters.equal("outputAlbumId", albumId)
    ))
    case CVModelIdIs(modelId) => Try(MongoFilters.equal("modelId", modelId))
  }

  override protected[prediction] def entityToDocument(entity: CVPrediction): Document = Document(
    "ownerId" -> entity.ownerId.toString,
    "modelId" -> entity.modelId,
    "name" -> entity.name,
    "inputAlbumId" -> entity.inputAlbumId,
    "outputAlbumId" -> entity.outputAlbumId,
    "status" -> predictionStatusToString(entity.status),
    "created" -> entity.created.toString,
    "updated" -> entity.updated.toString,
    "probabilityPredictionTableId" -> entity.probabilityPredictionTableId.map(BsonString(_)),
    "evaluationSummary" -> entity.evaluationSummary.map(evaluationSummaryToDocument),
    "predictionTimeSpentSummary" -> entity.predictionTimeSpentSummary.map(predictionTimeSpentSummaryToDocument),
    "evaluationTimeSpentSummary" -> entity.evaluateTimeSpentSummary
      .map(CVModelPipelineSerializer.evaluationTimeSpentSummaryToDocument),
    "description" -> entity.description.map(BsonString(_)),
    "cvModelPredictOptions" -> entity.cvModelPredictOptions.map(cvModelPredictOptionsToDocument)
  )

  override protected[prediction] def documentToEntity(document: Document): Try[CVPrediction] = Try {
    CVPrediction(
      ownerId = UUID.fromString(document.getMandatory[BsonString]("ownerId").getValue),
      modelId = document.getMandatory[BsonString]("modelId").getValue,
      name = document.getMandatory[BsonString]("name").getValue,
      inputAlbumId = document.getMandatory[BsonString]("inputAlbumId").getValue,
      outputAlbumId = document.getMandatory[BsonString]("outputAlbumId").getValue,
      status = stringToPredictionStatus(document.getMandatory[BsonString]("status").getValue),
      created = Instant.parse(document.getMandatory[BsonString]("created").getValue),
      updated = Instant.parse(document.getMandatory[BsonString]("updated").getValue),
      probabilityPredictionTableId = document.get[BsonString]("probabilityPredictionTableId").map(_.getValue),
      evaluationSummary = document.getChild("evaluationSummary").map(documentToEvaluationSummary),
      predictionTimeSpentSummary = document.getChild("predictionTimeSpentSummary")
        .map(documentToPredictionTimeSpentSummary),
      evaluateTimeSpentSummary = document.getChild("evaluationTimeSpentSummary")
        .map(CVModelPipelineSerializer.documentToEvaluationTimeSpentSummary),
      description = document.get[BsonString]("description").map(_.getValue),
      cvModelPredictOptions = document.getChild("cvModelPredictOptions").map(documentToCVModelPredictOptions)
    )
  }

  protected def predictionTimeSpentSummaryToDocument(summary: PredictionTimeSpentSummary): BsonDocument = BsonDocument(
    "dataFetchTime" -> BsonInt64(summary.dataFetchTime),
    "loadModelTime" -> BsonInt64(summary.loadModelTime),
    "predictionTime" -> BsonInt64(summary.predictionTime),
    "tasksQueuedTime" -> BsonInt64(summary.tasksQueuedTime),
    "totalJobTime" -> BsonInt64(summary.totalJobTime),
    "pipelineTimings" -> BsonArray(summary.pipelineTimings.map(CommonSerializers.pipelineTimingToDocument))
  )

  protected def documentToPredictionTimeSpentSummary(document: Document) = PredictionTimeSpentSummary(
    dataFetchTime = document.getMandatory[BsonInt64]("dataFetchTime").getValue,
    loadModelTime = document.getMandatory[BsonInt64]("loadModelTime").getValue,
    predictionTime = document.getMandatory[BsonInt64]("predictionTime").getValue,
    tasksQueuedTime = document.getMandatory[BsonInt64]("tasksQueuedTime").getValue,
    totalJobTime = document.getMandatory[BsonInt64]("totalJobTime").getValue,
    pipelineTimings = document.getMandatory[BsonArray]("pipelineTimings").map { elem =>
      CommonSerializers.documentToPipelineTiming(elem.asDocument)
    }.toList
  )

  protected def cvModelPredictOptionsToDocument(cvModelPredictOptions: CVModelPredictOptions) = BsonDocument(
    "loi" -> cvModelPredictOptions.loi.map(_.map(labelOfInterest =>
      BsonDocument(
        "label" -> BsonString(labelOfInterest.label),
        "threshold" -> BsonDouble(labelOfInterest.threshold)
      )
    )),
    "defaultVisualThreshold" -> cvModelPredictOptions.defaultVisualThreshold.map(BsonDouble(_)),
    "iouThreshold" -> cvModelPredictOptions.iouThreshold.map(BsonDouble(_))
  )

  protected def documentToCVModelPredictOptions(document: Document) = CVModelPredictOptions(
    loi = document.get[BsonArray]("loi").map(_.map { labelOfInterest =>
      val labelOfInterestDocument = Document(labelOfInterest.asDocument)
      LabelOfInterest(
        label = labelOfInterestDocument.getMandatory[BsonString]("label").getValue,
        threshold = labelOfInterestDocument.getMandatory[BsonDouble]("threshold").getValue
      )
    }),
    defaultVisualThreshold = document.get[BsonDouble]("defaultVisualThreshold").map(_.getValue.toFloat),
    iouThreshold = document.get[BsonDouble]("iouThreshold").map(_.getValue.toFloat)
  )

  private def stringToPredictionStatus(predictionStatus: String): CVPredictionStatus = {
    predictionStatus match {
      case "NEW" => CVPredictionStatus.New
      case "RUNNING" => CVPredictionStatus.Running
      case "ERROR" => CVPredictionStatus.Error
      case "DONE" => CVPredictionStatus.Done
    }
  }

  private def predictionStatusToString(predictionStatus: CVPredictionStatus): String = {
    predictionStatus match {
      case CVPredictionStatus.New => "NEW"
      case CVPredictionStatus.Running => "RUNNING"
      case CVPredictionStatus.Error => "ERROR"
      case CVPredictionStatus.Done => "DONE"
    }
  }

  protected def evaluationSummaryToDocument(summary: CVModelSummary) = BsonDocument(
    "labels" -> summary.labels,
    "confusionMatrix" -> summary.confusionMatrix.map(_.map(row =>
      BsonDocument(
        "actual" -> row.actualLabel.map(BsonInt32(_)),
        "predicted" -> row.predictedLabel.map(BsonInt32(_)),
        "count" -> BsonInt32(row.count)
      )
    )),
    "mAP" -> summary.mAP.map(BsonDouble(_)),
    "reconstructionLoss" -> summary.reconstructionLoss.map(BsonDouble(_))
  )

  protected def documentToEvaluationSummary(document: Document): CVModelSummary = CVModelSummary(
    labels = document.getMandatory[BsonArray]("labels").map(_.asString.getValue),
    confusionMatrix = document.get[BsonArray]("confusionMatrix").map(_.map { matrixRow =>
      val matrixRowDocument = Document(matrixRow.asDocument)
      ConfusionMatrixCell(
        actualLabel = matrixRowDocument.get[BsonInt32]("actual").map(_.getValue),
        predictedLabel = matrixRowDocument.get[BsonInt32]("predicted").map(_.getValue),
        count = matrixRowDocument.getMandatory[BsonInt32]("count").getValue
      )
    }),
    mAP = document.get[BsonDouble]("mAP").map(_.getValue),
    reconstructionLoss = document.get[BsonDouble]("reconstructionLoss").map(_.getValue)
  )

}
