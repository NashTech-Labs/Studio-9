package cortex.api.pegasus

sealed trait CreatedBy

object CreatedBy {
  case object Taurus extends CreatedBy
}
