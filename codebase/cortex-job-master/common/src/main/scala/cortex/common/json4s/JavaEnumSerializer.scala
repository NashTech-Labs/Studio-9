package cortex.common.json4s

import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

object JavaEnumSerializer {
  def apply[E <: java.lang.Enum[E]](implicit m: Manifest[E]): CustomSerializer[E] = {
    new CustomSerializer[E](_ => (
      { case JString(value) => Enum.valueOf(m.runtimeClass.asInstanceOf[Class[E]], value) },
      { case enum: E => JString(enum.name()) }
    ))
  }
}
