package orion.common

import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString
import orion.domain.service.{ Enum, EnumDeserializer }

object EnumSerializer {
  def apply[E <: Enum: Manifest](deserializer: EnumDeserializer[E]): CustomSerializer[E] = {
    new CustomSerializer[E](format => (
      { case JString(value) => deserializer.deserialize(value) },
      { case enum: E => JString(enum.serialize()) }
    ))
  }
}
