package com.sentrana.umserver.controllers

import javax.inject.{ Inject, Singleton }

import com.iheart.playSwagger.SwaggerSpecGenerator
import com.sentrana.umserver.UmSettings
import play.api.libs.json.JsString
import play.api.{ Configuration, Play }
import play.api.mvc.{ Action, Controller }

import scala.concurrent.Future

/**
 * Created by Paul Lysak on 17.05.16.
 */
@Singleton
class SwaggerApiController @Inject() (settings: UmSettings) extends Controller {
  private implicit val cl = getClass.getClassLoader

  private lazy val generator = SwaggerSpecGenerator(
    "com.sentrana.umserver.shared.dtos",
    "com.sentrana.umserver.dtos",
    "com.sentrana.umserver.exceptions"
  )

  def specs = Action {
    if (settings.swaggerEnabled)
      Ok(generator.generate("umserver.routes").map(_ + ("host" -> JsString(settings.host))).get)
    else
      NotFound
  }

}
