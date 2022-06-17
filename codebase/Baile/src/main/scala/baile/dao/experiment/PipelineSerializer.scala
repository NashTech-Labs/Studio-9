package baile.dao.experiment

import baile.daocommons.filters.Filter
import baile.domain.experiment.pipeline.ExperimentPipeline
import baile.domain.experiment.result.ExperimentResult
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson

import scala.util.Try

trait PipelineSerializer[P <: ExperimentPipeline, R <: ExperimentResult] {

  val filterMapper: PartialFunction[Filter, Try[Bson]]

  def pipelineToDocument(experimentPipeline: P): Document

  def documentToPipeline(document: Document): P

  def resultToDocument(result: R): Document

  def documentToResult(document: Document): R

}
