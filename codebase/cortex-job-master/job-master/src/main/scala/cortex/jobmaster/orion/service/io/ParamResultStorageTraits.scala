package cortex.jobmaster.orion.service.io

trait StorageWriter[T] {

  val marshaller: Marshaller[T, _]

  def put(value: T, path: String): String
}

trait StorageReader[T] {

  val unmarshaller: Unmarshaller[T, _]

  def get(path: String): T
}

trait StorageCleaner {

  def deleteRecursively(path: String): Unit
}
