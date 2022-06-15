package baile.services.remotestorage

import java.time.Instant

case class File(
  path: String,
  size: Long,
  lastModified: Instant
) extends StoredObject {
  override def updatePath(newPath: String): File = copy(path = newPath)
}
