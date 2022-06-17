package baile.routes.contract.tabular.model

import play.api.libs.json.{ JsString, JsValue, Writes }

sealed trait TabularModelClassResponse

object TabularModelClassResponse {

  case object Regression extends TabularModelClassResponse
  case object BinaryClassification extends TabularModelClassResponse
  case object Classification extends TabularModelClassResponse

  implicit val ModelTypeResponseWrites: Writes[TabularModelClassResponse] = new Writes[TabularModelClassResponse] {
    override def writes(modelType: TabularModelClassResponse): JsValue = modelType match {
      case TabularModelClassResponse.Regression => JsString("REGRESSION")
      case TabularModelClassResponse.BinaryClassification => JsString("BINARY_CLASSIFICATION")
      case TabularModelClassResponse.Classification => JsString("CLASSIFICATION")
    }
  }

}
