package baile.routes.contract.pipeline.operator

import baile.domain.pipeline.PrimitiveDataType
import baile.utils.json.EnumWritesBuilder
import play.api.libs.json.Writes

sealed trait PrimitiveDataTypeResponse extends PipelineDataTypeResponse

object PrimitiveDataTypeResponse {

  case object String extends PrimitiveDataTypeResponse

  case object Integer extends PrimitiveDataTypeResponse

  case object Boolean extends PrimitiveDataTypeResponse

  case object Float extends PrimitiveDataTypeResponse

  implicit val PrimitiveDataTypeResponseWrites: Writes[PrimitiveDataTypeResponse] = EnumWritesBuilder.build {
    case PrimitiveDataTypeResponse.String => "string"
    case PrimitiveDataTypeResponse.Integer => "integer"
    case PrimitiveDataTypeResponse.Boolean => "boolean"
    case PrimitiveDataTypeResponse.Float => "float"
  }

  def fromDomain(primitiveDataType: PrimitiveDataType): PrimitiveDataTypeResponse = {
    primitiveDataType match {
      case PrimitiveDataType.String => PrimitiveDataTypeResponse.String
      case PrimitiveDataType.Integer => PrimitiveDataTypeResponse.Integer
      case PrimitiveDataType.Boolean => PrimitiveDataTypeResponse.Boolean
      case PrimitiveDataType.Float => PrimitiveDataTypeResponse.Float
    }
  }

}
