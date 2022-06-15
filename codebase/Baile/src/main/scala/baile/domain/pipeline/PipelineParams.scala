package baile.domain.pipeline

object PipelineParams {

  type PipelineParams = Map[String, PipelineParam]

  sealed trait PipelineParam

  case class StringParam(value: String) extends PipelineParam
  case class IntParam(value: Int) extends PipelineParam
  case class FloatParam(value: Float) extends PipelineParam
  case class BooleanParam(value: Boolean) extends PipelineParam
  case class StringParams(values: Seq[String]) extends PipelineParam
  case class IntParams(values: Seq[Int]) extends PipelineParam
  case class FloatParams(values: Seq[Float]) extends PipelineParam
  case class BooleanParams(values: Seq[Boolean]) extends PipelineParam
  case object EmptySeqParam extends PipelineParam
}
