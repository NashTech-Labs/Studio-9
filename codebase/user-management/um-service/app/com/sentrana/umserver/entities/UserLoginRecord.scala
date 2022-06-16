package com.sentrana.umserver.entities

import java.time.ZonedDateTime

import com.sentrana.umserver.shared.dtos.WithId
import play.api.libs.json.Json

import scala.concurrent.Future

/**
 * Created by Alexander on 21.04.2016.
 */
case class UserLoginRecord(
  id:            String,
  loginIp:       String,
  loginResult:   Boolean,
  loginUserName: String,
  loginUserId:   Option[String] = None,
  requestOrgId:  Option[String] = None,
  loginTime:     ZonedDateTime  = ZonedDateTime.now()
) extends WithId

object UserLoginRecord {
  implicit val userLoginRecordFormat = Json.format[UserLoginRecord]
}

