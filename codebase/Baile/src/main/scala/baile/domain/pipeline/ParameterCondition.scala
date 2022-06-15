package baile.domain.pipeline

sealed trait ParameterCondition

case class BooleanParameterCondition(
  value: Boolean
) extends ParameterCondition

case class FloatParameterCondition(
  values: Seq[Float],
  min: Option[Float],
  max: Option[Float]
) extends ParameterCondition

case class IntParameterCondition(
  values: Seq[Int],
  min: Option[Int],
  max: Option[Int]
) extends ParameterCondition

case class StringParameterCondition(
  values: Seq[String]
) extends ParameterCondition
