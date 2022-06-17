package com.sentrana.umserver.shared

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import com.sentrana.umserver.shared.dtos.enums._
import play.api.libs.json._

/**
 * Created by Paul Lysak on 26.04.16.
 */
class JsonFormatsShared {
  def enumReads[T <: Enum[T]](mkEnum: String => T) = new Reads[T] {
    override def reads(json: JsValue): JsResult[T] =
      json match {
        case JsString(s) => try {
          JsSuccess(mkEnum(s))
        } catch {
          case e: IllegalArgumentException =>
            JsError("Not a valid enum value: " + s)
        }
        case v => JsError("Can't convert to enum: " + v)
      }
  }

  implicit val timeWrites: Writes[ZonedDateTime] =
    Writes.temporalWrites[ZonedDateTime, DateTimeFormatter](DateTimeFormatter.ISO_ZONED_DATE_TIME)

  implicit val enumWrites = new Writes[Enum[_]] {
    def writes(e: Enum[_]) = JsString(e.toString)
  }

  implicit val userStatusReads = enumReads(UserStatus.valueOf)

  implicit val orgStatusReads = enumReads(OrganizationStatus.valueOf)

  implicit val dbTypeReads = enumReads(DBType.valueOf)

  implicit val filterOperatorReads = enumReads(FilterOperator.valueOf)

}

