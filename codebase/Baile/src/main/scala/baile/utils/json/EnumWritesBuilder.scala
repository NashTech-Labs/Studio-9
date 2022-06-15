package baile.utils.json

import play.api.libs.json.{ JsString, Writes }

object EnumWritesBuilder {

  def build[T](valueToString: T => String): Writes[T] = Writes[T](valueToString andThen JsString)

}
