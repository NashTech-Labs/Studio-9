package baile.routes.contract.pipeline.operator

import baile.domain.pipeline.{ ComplexDataType, PrimitiveDataType, PipelineDataType => DomainPipelineDataType }
import baile.routes.contract.pipeline.operator.ComplexDataTypeResponse.ComplexDataTypeResponseWrites
import baile.routes.contract.pipeline.operator.PrimitiveDataTypeResponse.PrimitiveDataTypeResponseWrites
import play.api.libs.json.{ JsValue, Writes }

trait PipelineDataTypeResponse

object PipelineDataTypeResponse {

  implicit val PipelineDataTypeWrites: Writes[PipelineDataTypeResponse] = new Writes[PipelineDataTypeResponse] {
    override def writes(response: PipelineDataTypeResponse): JsValue = response match {
      case data: ComplexDataTypeResponse => ComplexDataTypeResponseWrites.writes(data)
      case data: PrimitiveDataTypeResponse => PrimitiveDataTypeResponseWrites.writes(data)
      case _ => throw new IllegalArgumentException(
        s"PipelineDataType serializer can't serialize ${ response.getClass }"
      )
    }
  }

  def fromDomain(pipelineDataType: DomainPipelineDataType): PipelineDataTypeResponse =
    pipelineDataType match {
      case primitiveDataType: PrimitiveDataType => PrimitiveDataTypeResponse.fromDomain(primitiveDataType)
      case complexDataType: ComplexDataType => ComplexDataTypeResponse.fromDomain(complexDataType)
    }

}
