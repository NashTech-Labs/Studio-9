package baile.domain.pipeline.result

sealed trait PipelineResultValue

object PipelineResultValue {

  case class IntValue(value: Int) extends PipelineResultValue

  case class FloatValue(value: Float) extends PipelineResultValue

  case class StringValue(value: String) extends PipelineResultValue

  case class BooleanValue(value: Boolean) extends PipelineResultValue

}
