package baile.domain.pipeline

trait PipelineDataType

sealed trait PrimitiveDataType extends PipelineDataType

object PrimitiveDataType {

  case object String extends PrimitiveDataType

  case object Integer extends PrimitiveDataType

  case object Boolean extends PrimitiveDataType

  case object Float extends PrimitiveDataType

}

case class ComplexDataType(
  definition: String,
  parents: Seq[ComplexDataType],
  typeArguments: Seq[PipelineDataType]
) extends PipelineDataType
