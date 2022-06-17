package aries.common.json4s

import aries.domain.service.{ Enum, EnumDeserializer }
import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

object EnumSerializer {
  def apply[E <: Enum: Manifest](deserializer: EnumDeserializer[E]): CustomSerializer[E] = {
    new CustomSerializer[E](_ => (
      { case JString(value) => deserializer.deserialize(value) },
      { case enum: E => JString(enum.serialize()) }
    ))
  }
}

object JavaEnumSerializer {
  def apply[E <: java.lang.Enum[E]](implicit m: Manifest[E]): CustomSerializer[E] = {
    new CustomSerializer[E](_ => (
      { case JString(value) => Enum.valueOf(m.runtimeClass.asInstanceOf[Class[E]], value) },
      { case enum: E => JString(enum.name()) }
    ))
  }
}
