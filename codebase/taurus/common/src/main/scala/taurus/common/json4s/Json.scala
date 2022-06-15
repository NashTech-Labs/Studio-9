package taurus.common.json4s

import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.reflect.Manifest
import scala.util.{ Failure, Success, Try }

object Json {

  def toJson[T](value: T)(implicit formats: Formats): String = {
    compact(render(Extraction.decompose(value)))
  }

  def fromJson[T: Manifest](json: String)(implicit formats: Formats): T = {
    Extraction.extract[T](jackson.parseJson(json))
  }

  def fromJsonOpt[T: Manifest](json: String)(implicit formats: Formats): Option[T] = {
    Extraction.extractOpt[T](jackson.parseJson(json))
  }

  def fromJsonEither[T: Manifest](json: String)(implicit formats: Formats): Either[Throwable, T] = {
    Try(fromJson[T](json)) match {
      case Success(result) => Right(result)
      case Failure(err)    => Left(err)
    }
  }

  def toJValue(json: String): JValue = {
    parse(json)
  }

  def toMap(json: String)(implicit formats: Formats): Map[String, Any] = {
    parse(json).extract[Map[String, Any]]
  }

  def fieldByName[T: Manifest](json: String, fieldName: String)(implicit formats: Formats): T = {
    Extraction.extract[T](parse(json) \ fieldName)
  }
}
