package taurus.common.rest.routes

import scala.util.Try

trait Translator[-From, +To] {

  def translate(from: From): To

  def tryToTranslate(from: From): Try[To] = Try(translate(from))
}

