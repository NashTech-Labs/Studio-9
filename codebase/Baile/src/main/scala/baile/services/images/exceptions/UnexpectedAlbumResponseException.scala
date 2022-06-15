package baile.services.images.exceptions

import baile.services.images.AlbumService.AlbumServiceError

case class UnexpectedAlbumResponseException(
  error: AlbumServiceError
) extends RuntimeException(
  s"Unexpected response in Album Service : '$error'"
)
