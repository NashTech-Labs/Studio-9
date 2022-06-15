package baile.domain.cv.model.tlprimitives

import baile.domain.pipeline.OperatorParameter

case class CVTLModelPrimitive(
  packageId: String,
  name: String,
  description: Option[String],
  moduleName: String,
  className: String,
  cvTLModelPrimitiveType: CVTLModelPrimitiveType,
  params: Seq[OperatorParameter],
  isNeural: Boolean
)
