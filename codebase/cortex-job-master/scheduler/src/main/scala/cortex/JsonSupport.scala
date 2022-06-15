package cortex

import play.api.libs.json._

object JsonSupport {
  val SnakeJson: Json.WithOptions[Json.MacroOptions] = Json.configured(JsonConfiguration(SnakeCase))

  implicit val optionStringFormat: Format[Option[String]] = play.api.libs.json.Format.optionWithNull[String]

  def toString[T](obj: T)(implicit writes: Writes[T]): String = Json.toJson(obj)(writes).toString()

  def fromString[T](str: String)(implicit reads: Reads[T]): T = Json.parse(str).as[T](reads)

  /**
   * For each class property, use the snake case equivalent
   * to name its column (e.g. fooBar -> foo_bar, mAP -> m_a_p).
   */
  private object SnakeCase extends JsonNaming {
    def apply(property: String): String = {
      val result = property.flatMap { c =>
        if (c.isUpper) Seq('_', c.toLower) else Seq(c)
      }
      if (result.startsWith("_")) result.tail else result
    }

    override val toString = "SnakeCase"
  }

}
