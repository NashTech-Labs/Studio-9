package pegasus

package object testkit {

  def resourceAsLines(resource: String): List[String] = {
    val dataStream = getClass.getResourceAsStream(resource)
    scala.io.Source.fromInputStream(dataStream).getLines.toList
  }
}
