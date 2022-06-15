package cortex.common

object Utils {

  /**
   * Cuts base part of some fs path leaving only relative part
   * @param basePath a part to remove
   * @param target initial path
   */
  def cutBasePath(basePath: String, target: String): String = {
    val strippedBasePath = basePath.stripSuffix("/")
    target.replaceFirst(strippedBasePath, "").toList match {
      case '/' :: Nil  => ""
      case '/' :: tail => tail.mkString
      case _           => throw new IllegalArgumentException(s"Target path: [$target] doesn't contain a base path: [$basePath]")
    }
  }
}
