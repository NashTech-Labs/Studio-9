package orion.domain.service

trait Enum {
  def serialize(): String
}

trait EnumDeserializer[T <: Enum] {
  val All: Seq[T]

  private def map: Map[String, T] = All.map(e => (e.serialize, e)).toMap

  def deserialize(value: String): T = {
    map.get(value).getOrElse(throw new IllegalArgumentException(s"Invalid value '$value' - Possible values are: ${map.keys.mkString(", ")}"))
  }
}
