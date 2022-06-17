package orion.common.serialization

import java.nio.charset.{ Charset, StandardCharsets }

import akka.serialization.SerializerWithStringManifest
import play.api.libs.json._

class PlayJsonSerializer[T](entityClass: Class[T], format: Format[T]) extends SerializerWithStringManifest {
  private val entityClassManifest = entityClass.getName
  private val charset: Charset = StandardCharsets.UTF_8
  override val identifier: Int = "play-json-serializer".##

  override def manifest(o: AnyRef): String = {
    o.getClass.getName
  }

  override def toBinary(o: AnyRef): Array[Byte] = {
    val manifestClassName = manifest(o)
    if (entityClassManifest == manifestClassName) {
      val json = format.writes(o.asInstanceOf[T])
      val bytes: Array[Byte] = Json.stringify(json).getBytes(charset)

      bytes
    } else {
      throw new RuntimeException(s"Expected class [$entityClassManifest], found class [$manifestClassName]")
    }
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    if (entityClassManifest == manifest) {
      format.reads(Json.parse(bytes)) match {
        case JsSuccess(obj, _) =>
          obj.asInstanceOf[AnyRef]
        case JsError(errors) =>
          throw new SerializationException(s"Failed to deserialize bytes with manifest [$manifest]", errors)
      }

    } else {
      throw new RuntimeException(s"Expected class [$entityClassManifest], found class [$manifest]")
    }
  }
}
