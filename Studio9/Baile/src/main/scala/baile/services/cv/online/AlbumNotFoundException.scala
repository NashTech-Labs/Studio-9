package baile.services.cv.online

case class AlbumNotFoundException (
  albumid: String
) extends RuntimeException(
  s"Album with id '$albumid' not found"
)

