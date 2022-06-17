package sqlserver.utils.json

import play.api.libs.json.Format

object EnumFormatBuilder {

  def build[T](
    stringToValue: PartialFunction[String, T],
    valueToString: T => String,
    typeName: String = ""
  ): Format[T] =
    Format(EnumReadsBuilder.build(stringToValue, typeName), EnumWritesBuilder.build(valueToString))

}
