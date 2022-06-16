package com.sentrana.umserver.dtos

import play.api.libs.json.{ Json, Writes }
import play.api.mvc.{ Result, Results }

case class ActivationResponse(message: String) {

  def result: Result = Results.Ok(Json.toJson(this))

}

object ActivationResponse {

  implicit val ActivationResponseWrites: Writes[ActivationResponse] =
    Json.writes[ActivationResponse]

}
