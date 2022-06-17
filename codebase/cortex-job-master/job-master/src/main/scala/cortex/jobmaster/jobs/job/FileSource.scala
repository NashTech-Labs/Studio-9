package cortex.jobmaster.jobs.job

trait FileSource[T] {
  def baseRelativePath: Option[String]

  def getFiles: Seq[T]

}

object FileSource {
  def getFullPath(baseRelativePath: Option[String], filename: String): String = {
    baseRelativePath match {
      case Some(x) => s"$x/$filename"
      case None    => filename
    }
  }
}
