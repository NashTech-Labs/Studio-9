package pegasus.common.utils

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import scala.reflect.Manifest
import scala.util.Failure
import scala.util.Success
import scala.util.Try

trait JsonSupport extends {

  implicit def formats: Formats = Serialization.formats(NoTypeHints) ++ org.json4s.ext.JodaTimeSerializers.all

  def toJson: String = compact(render(Extraction.decompose(this).snakizeKeys))

  def fromJson[T: Manifest](json: String): T = Extraction.extract[T](jackson.parseJson(json).camelizeKeys)

  def fromJsonOpt[T: Manifest](json: String): Option[T] = Extraction.extractOpt[T](jackson.parseJson(json).camelizeKeys)

  def fromJsonEither[T: Manifest](json: String): Either[Throwable, T] = {
    Try(fromJson[T](json)) match {
      case Success(result) => Right(result)
      case Failure(err)    => Left(err)
    }
  }

  def toJValue(json: String): JValue = parse(json)

  def toMap(json: String): Map[String, Any] = parse(json).extract[Map[String, Any]]

  def fieldByName[T: Manifest](json: String, fieldName: String): T = Extraction.extract[T](parse(json) \ fieldName)
}