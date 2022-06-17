package baile.services.remotestorage

trait StoredObject {

  val path: String

  def updatePath(newPath: String): StoredObject

}
