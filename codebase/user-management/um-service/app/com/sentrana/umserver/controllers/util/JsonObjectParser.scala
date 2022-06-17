package com.sentrana.umserver.controllers.util

import play.api.data.validation.ValidationError
import play.api.libs.json.{ Reads, JsError, Json, JsPath }
import play.api.mvc.{ Result, Results, BodyParsers }
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by Paul Lysak on 12.04.16.
 */
trait JsonObjectParser {
  this: BodyParsers with Results =>

  private def jsonErrHandler(errors: Seq[(JsPath, Seq[ValidationError])]): Result = {
    BadRequest(Json.obj("message" -> JsError.toJson(errors).toString))
  }

  protected def parseObj[A](maxLength: Int = parse.DefaultMaxTextLength)(implicit rds: Reads[A]) = parse.using(request =>
    parse.json(maxLength).validate({ json =>
      json.validate[A].fold(
        errs => Left(jsonErrHandler(errs)),
        Right.apply
      )
    }))
}
