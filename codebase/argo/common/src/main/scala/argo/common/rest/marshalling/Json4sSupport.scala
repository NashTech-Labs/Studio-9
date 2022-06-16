package argo.common.rest.marshalling

import java.lang.reflect.InvocationTargetException

import akka.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import akka.util.ByteString
import org.json4s.{ Formats, MappingException, Serialization }

trait SnakeCaseJson4sHttpSupport extends Json4sHttpSupport {
  implicit val snakeCaseEnabled = Json4sHttpSupport.SnakeCase.Enabled
}

/**
 * Automatic to and from JSON marshalling/unmarshalling using an in-scope *Json4s* protocol.
 *
 * Snake case is enabled if an implicit [[Json4sHttpSupport.SnakeCase.Enabled]] is in scope.
 */
object Json4sHttpSupport extends Json4sHttpSupport {

  sealed trait SnakeCase

  object SnakeCase {
    object Enabled extends SnakeCase
    object Disabled extends SnakeCase
  }
}

/**
 * Automatic to and from JSON marshalling/unmarshalling using an in-scope *Json4s* protocol.
 *
 * Snake case is enabled if an implicit [[Json4sHttpSupport.SnakeCase.Enabled]] is in scope.
 */
trait Json4sHttpSupport {
  import Json4sHttpSupport._

  private val jsonStringUnmarshaller = {
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(`application/json`)
      .mapWithCharset {
        case (ByteString.empty, _) => throw Unmarshaller.NoContentException
        case (data, charset)       => data.decodeString(charset.nioCharset.name)
      }
  }

  private val jsonStringMarshaller = {
    Marshaller.stringMarshaller(`application/json`)
  }

  private val snakeJsonStringMarshaller = {
    def snakize(json: String): String = {
      import org.json4s.jackson.JsonMethods._
      compact(render(parse(json).snakizeKeys))
    }

    jsonStringMarshaller.compose(snakize)
  }

  private val snakeJsonStringUnmarshaller = {
    def camelize(json: String): String = {
      import org.json4s.jackson.JsonMethods._
      compact(parse(json).camelizeKeys)
    }

    jsonStringUnmarshaller.map(camelize)
  }

  private def getBaseUnmarshaller()(implicit snakeCase: SnakeCase): FromEntityUnmarshaller[String] = {
    snakeCase match {
      case SnakeCase.Enabled  => snakeJsonStringUnmarshaller
      case SnakeCase.Disabled => jsonStringUnmarshaller
    }
  }

  private def getBaseMarshaller()(implicit snakeCase: SnakeCase): ToEntityMarshaller[String] = {
    snakeCase match {
      case SnakeCase.Enabled  => snakeJsonStringMarshaller
      case SnakeCase.Disabled => jsonStringMarshaller
    }
  }

  /**
   * HTTP entity => `A`
   *
   * @tparam A type to decode
   * @return unmarshaller for `A`
   */
  implicit def json4sUnmarshaller[A: Manifest](
    implicit
    serialization: Serialization,
    formats:       Formats,
    snakeCase:     SnakeCase     = SnakeCase.Disabled
  ): FromEntityUnmarshaller[A] = {

    getBaseUnmarshaller().map(data => serialization.read(data))
      .recover(_ => _ => {
        case MappingException("unknown error", ite: InvocationTargetException) => throw ite.getCause
      })
  }

  /**
   * `A` => HTTP entity
   *
   * @tparam A type to encode, must be upper bounded by `AnyRef`
   * @return marshaller for any `A` value
   */
  implicit def json4sMarshaller[A <: AnyRef](
    implicit
    serialization: Serialization,
    formats:       Formats,
    snakeCase:     SnakeCase     = SnakeCase.Disabled
  ): ToEntityMarshaller[A] = {

    getBaseMarshaller().compose(serialization.write[A])
  }

}