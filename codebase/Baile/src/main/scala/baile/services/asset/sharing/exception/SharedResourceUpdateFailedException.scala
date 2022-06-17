package baile.services.asset.sharing.exception

case class SharedResourceUpdateFailedException(id: String) extends RuntimeException(
  s"Update returned no result for shared resource with id: '$id'.")
