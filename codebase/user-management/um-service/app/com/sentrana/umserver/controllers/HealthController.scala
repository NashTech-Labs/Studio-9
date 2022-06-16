package com.sentrana.umserver.controllers

import javax.inject.{ Inject, Singleton }

import play.api.Play.current
import play.api.mvc.{ Action, Controller }

@Singleton
class HealthController @Inject() extends Controller {

  def healthCheck = Action {
    Ok
  }
}