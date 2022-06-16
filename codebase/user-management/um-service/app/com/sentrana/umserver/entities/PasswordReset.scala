package com.sentrana.umserver.entities

import java.time.ZonedDateTime

import com.sentrana.umserver.shared.dtos.WithId
import com.sentrana.umserver.shared.dtos.enums.PasswordResetStatus
import play.api.libs.json.Json

/**
 * Created by Paul Lysak on 06.09.16.
 */
case class PasswordReset(
  id:         String,
  secretCode: String,
  status:     PasswordResetStatus,
  userId:     String,
  email:      String,
  created:    ZonedDateTime       = ZonedDateTime.now(),
  updated:    ZonedDateTime       = ZonedDateTime.now()
) extends WithId

object PasswordReset {
  import com.sentrana.umserver.JsonFormats._

  implicit val passwordResetStatusReads = enumReads(PasswordResetStatus.valueOf)

  implicit val passwordResetFormat = Json.format[PasswordReset]
}
