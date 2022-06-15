package baile.services.cv.online

case class AlbumIsNotForPredictionException (
  albumid: String
) extends RuntimeException(
  s"Album '$albumid' is not suitable for prediction results storing"
)

