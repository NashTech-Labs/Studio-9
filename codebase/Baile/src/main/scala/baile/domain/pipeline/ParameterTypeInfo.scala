package baile.domain.pipeline

import baile.domain.asset.AssetType

sealed trait ParameterTypeInfo

case class BooleanParameterTypeInfo(
  default: Seq[Boolean]
) extends ParameterTypeInfo

case class FloatParameterTypeInfo(
  values: Seq[Float],
  default: Seq[Float],
  min: Option[Float],
  max: Option[Float],
  step: Option[Float]
) extends ParameterTypeInfo

case class IntParameterTypeInfo(
  values: Seq[Int],
  default: Seq[Int],
  min: Option[Int],
  max: Option[Int],
  step: Option[Int]
) extends ParameterTypeInfo

case class StringParameterTypeInfo(
  values: Seq[String],
  default: Seq[String]
) extends ParameterTypeInfo

case class AssetParameterTypeInfo(
  assetType: AssetType
) extends ParameterTypeInfo
