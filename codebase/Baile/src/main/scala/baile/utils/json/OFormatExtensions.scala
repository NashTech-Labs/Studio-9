package baile.utils.json

import play.api.libs.json._

object OFormatExtensions {

  implicit class OFormatExtensions[A](oFormat: OFormat[A]) {

    def withField[B](name: String, f: A => B)(implicit bWrites: Writes[B]): OFormat[A] = {
      new OFormat[A] {
        override def writes(o: A): JsObject =
          oFormat.writes(o) + (name -> bWrites.writes(f(o)))

        override def reads(json: JsValue): JsResult[A] =
          oFormat.reads(json)
      }
    }
  }

}
