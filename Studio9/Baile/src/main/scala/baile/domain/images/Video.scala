package baile.domain.images

case class Video(
  filePath: String,
  fileSize: Long,
  fileName: String,
  frameRate: Int,
  frameCaptureRate: Int,
  height: Int,
  width: Int
)
