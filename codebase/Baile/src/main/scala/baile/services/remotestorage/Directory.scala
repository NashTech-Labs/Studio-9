package baile.services.remotestorage

case class Directory(path: String) extends StoredObject {
  override def updatePath(newPath: String): Directory = copy(path = newPath)
}
