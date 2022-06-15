package baile.dao

import baile.dao.mongo.BsonHelpers._
import baile.domain.asset.AssetType
import baile.domain.common.{ ClassReference, ConfusionMatrixCell }
import baile.domain.job.PipelineTiming
import baile.domain.table.{ ColumnDataType, ColumnVariableType }
import org.mongodb.scala.Document
import org.mongodb.scala.bson.{ BsonDocument, BsonInt32, BsonInt64, BsonString }

private[dao] object CommonSerializers {

  private val ColumnDataTypeMap: Map[ColumnDataType, String] = Map(
    ColumnDataType.Boolean -> "BOOLEAN",
    ColumnDataType.String -> "STRING",
    ColumnDataType.Integer -> "INTEGER",
    ColumnDataType.Timestamp -> "TIMESTAMP",
    ColumnDataType.Long -> "LONG",
    ColumnDataType.Double -> "DOUBLE"
  )
  private val ColumnDataTypeMapReversed = ColumnDataTypeMap.map(_.swap)

  private val ColumnVariableTypeMap: Map[ColumnVariableType, String] = Map(
    ColumnVariableType.Continuous -> "CONTINUOUS",
    ColumnVariableType.Categorical -> "CATEGORICAL"
  )
  private val ColumnVariableTypeMapReversed = ColumnVariableTypeMap.map(_.swap)

  def assetTypeFromString(raw: String): AssetType = raw match {
    case "TABULAR_PREDICTION" => AssetType.TabularPrediction
    case "TABULAR_MODEL" => AssetType.TabularModel
    case "TABLE" => AssetType.Table
    case "FLOW" => AssetType.Flow
    case "ALBUM" => AssetType.Album
    case "CV_MODEL" => AssetType.CvModel
    case "CV_PREDICTION" => AssetType.CvPrediction
    case "ONLINE_JOB" => AssetType.OnlineJob
    case "DC_PROJECT" => AssetType.DCProject
    case "EXPERIMENT" => AssetType.Experiment
    case "PIPELINE" => AssetType.Pipeline
    case "DATASET" => AssetType.Dataset
  }

  def assetTypeToString(assetType: AssetType): String = assetType match {
    case AssetType.TabularPrediction => "TABULAR_PREDICTION"
    case AssetType.TabularModel => "TABULAR_MODEL"
    case AssetType.Table => "TABLE"
    case AssetType.Flow => "FLOW"
    case AssetType.Album => "ALBUM"
    case AssetType.CvModel => "CV_MODEL"
    case AssetType.CvPrediction => "CV_PREDICTION"
    case AssetType.OnlineJob => "ONLINE_JOB"
    case AssetType.DCProject => "DC_PROJECT"
    case AssetType.Experiment => "EXPERIMENT"
    case AssetType.Pipeline => "PIPELINE"
    case AssetType.Dataset => "DATASET"
  }

  def columnDataTypeFromString(raw: String): ColumnDataType = ColumnDataTypeMapReversed(raw)
  def columnDataTypeToString(columnDataType: ColumnDataType): String = ColumnDataTypeMap(columnDataType)

  def columnVariableTypeFromString(raw: String): ColumnVariableType = ColumnVariableTypeMapReversed(raw)
  def columnVariableTypeToString(columnVariableType: ColumnVariableType): String =
    ColumnVariableTypeMap(columnVariableType)

  def pipelineTimingToDocument(pipelineTiming: PipelineTiming): BsonDocument =
    BsonDocument(
      "description" -> BsonString(pipelineTiming.description),
      "time" -> BsonInt64(pipelineTiming.time)
    )

  def documentToPipelineTiming(document: Document): PipelineTiming =
    PipelineTiming(
      description = document.getMandatory[BsonString]("description").getValue,
      time = document.getMandatory[BsonInt64]("time").getValue
    )

  def classReferenceToDocument(classReference: ClassReference): Document = {
    Document(
      "packageId" -> BsonString(classReference.packageId),
      "moduleName" -> BsonString(classReference.moduleName),
      "className" -> BsonString(classReference.className)
    )
  }

  def documentToClassReference(document: Document): ClassReference = {
    ClassReference(
      packageId = document.getMandatory[BsonString]("packageId").getValue,
      moduleName = document.getMandatory[BsonString]("moduleName").getValue,
      className = document.getMandatory[BsonString]("className").getValue
    )
  }

  def confusionMatrixCellToDocument(matrixCell: ConfusionMatrixCell): Document = {
    Document(
      "actual" -> matrixCell.actualLabel.map(BsonInt32(_)),
      "predicted" -> matrixCell.predictedLabel.map(BsonInt32(_)),
      "count" -> BsonInt32(matrixCell.count)
    )
  }

  def documentToConfusionMatrixCell(document: Document): ConfusionMatrixCell =
    ConfusionMatrixCell(
      actualLabel = document.get[BsonInt32]("actual").map(_.getValue),
      predictedLabel = document.get[BsonInt32]("predicted").map(_.getValue),
      count = document.getMandatory[BsonInt32]("count").getValue
    )

}
