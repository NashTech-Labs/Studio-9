package com.sentrana.umserver.dtos

import play.api.libs.json.{ Json, Writes }
import play.api.mvc.{ Result, Results }

case class ErrorResponse(error: String, error_description: Option[String] = None) {

  def result: Result = Results.BadRequest(Json.toJson(this))

  implicit val errorResponseWrites: Writes[ErrorResponse] = Json.writes[ErrorResponse]

}

