package baile.services.cv.model

case class CortexModelIdNotFoundException(modelId: String) extends RuntimeException(
  s"Not found cortex model id for model $modelId which is expected to have it"
)
