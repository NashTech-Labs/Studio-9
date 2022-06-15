package baile.dao.experiment

import baile.dao.cv.model.CVModelPipelineSerializer
import baile.dao.mongo.BsonHelpers._
import baile.dao.pipeline.GenericExperimentPipelineSerializer
import baile.dao.tabular.model.TabularModelPipelineSerializer
import baile.daocommons.filters.Filter
import baile.domain.asset.AssetReference
import baile.domain.cv.pipeline.CVTLTrainPipeline
import baile.domain.cv.result.CVTLTrainResult
import baile.domain.experiment.pipeline.ExperimentPipeline
import baile.domain.experiment.result.ExperimentResult
import baile.domain.pipeline.pipeline.GenericExperimentPipeline
import baile.domain.pipeline.result.GenericExperimentResult
import baile.domain.tabular.pipeline.TabularTrainPipeline
import baile.domain.tabular.result.TabularTrainResult
import baile.utils.TryExtensions._
import org.mongodb.scala.Document
import org.mongodb.scala.bson.BsonString
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Filters.equal

import scala.util.Try

class SerializerDelegator(
  tabularModelPipelineSerializer: TabularModelPipelineSerializer,
  cvModelPipelineSerializer: CVModelPipelineSerializer,
  genericExperimentPipelineSerializer: GenericExperimentPipelineSerializer
) {

  val filterMapper: PartialFunction[Filter, Try[Bson]] =
    new PartialFunction[Filter, Try[Bson]] {
      override def isDefinedAt(x: Filter): Boolean =
        tabularModelPipelineSerializer.filterMapper.isDefinedAt(x) ||
          cvModelPipelineSerializer.filterMapper.isDefinedAt(x) ||
          genericExperimentPipelineSerializer.filterMapper.isDefinedAt(x)

      override def apply(f: Filter): Try[Bson] = {

        def buildFilterMapper(
          basePF: PartialFunction[Filter, Try[Bson]],
          typeName: String
        ): PartialFunction[Filter, Try[Bson]] =
          basePF.andThen(_.map(Filters.and(equal("pipeline._type", typeName), _)))

        Try.sequence(
          Seq(
            buildFilterMapper(tabularModelPipelineSerializer.filterMapper, "TabularTrainPipeline"),
            buildFilterMapper(cvModelPipelineSerializer.filterMapper, "CVTLTrainPipeline"),
            buildFilterMapper(genericExperimentPipelineSerializer.filterMapper, "GenericExperimentPipeline")
          ).flatMap(_.lift(f))
        ).map(Filters.or(_: _*))
      }
    }

  def pipelineToDocument(pipeline: ExperimentPipeline): Document = pipeline match {
    case tabularTrainPipeline: TabularTrainPipeline =>
      Document("_type"-> "TabularTrainPipeline") ++
        tabularModelPipelineSerializer.pipelineToDocument(tabularTrainPipeline)
    case cvtlTrainPipeline: CVTLTrainPipeline =>
      Document("_type" -> "CVTLTrainPipeline") ++ cvModelPipelineSerializer.pipelineToDocument(cvtlTrainPipeline)
    case genericExperimentPipeline: GenericExperimentPipeline =>
      Document("_type" -> "GenericExperimentPipeline") ++
        genericExperimentPipelineSerializer.pipelineToDocument(genericExperimentPipeline)
  }

  def documentToPipeline(document: Document): ExperimentPipeline =
    document.getMandatory[BsonString]("_type").getValue match {
      case "TabularTrainPipeline" => tabularModelPipelineSerializer.documentToPipeline(document)
      case "CVTLTrainPipeline" => cvModelPipelineSerializer.documentToPipeline(document)
      case "GenericExperimentPipeline" => genericExperimentPipelineSerializer.documentToPipeline(document)
    }

  def resultToDocument(result: ExperimentResult): Document = result match {
    case tabularTrainResult: TabularTrainResult =>
      Document("_type" -> "TabularTrainResult") ++ tabularModelPipelineSerializer.resultToDocument(tabularTrainResult)
    case cvtlTrainResult: CVTLTrainResult =>
      Document("_type" -> "CVTLTrainResult") ++ cvModelPipelineSerializer.resultToDocument(cvtlTrainResult)
    case genericExperimentResult: GenericExperimentResult =>
      Document("_type" -> "GenericExperimentResult") ++
        genericExperimentPipelineSerializer.resultToDocument(genericExperimentResult)
  }

  def documentToResult(document: Document): ExperimentResult =
    document.getMandatory[BsonString]("_type").getValue match {
      case "TabularTrainResult" => tabularModelPipelineSerializer.documentToResult(document)
      case "CVTLTrainResult" => cvModelPipelineSerializer.documentToResult(document)
      case "GenericExperimentResult" => genericExperimentPipelineSerializer.documentToResult(document)
    }

}

object SerializerDelegator {

  case class HasAssetReference(assetReference: AssetReference) extends Filter
}
