package cortex.task.transform.common

import play.api.libs.functional.syntax._
import play.api.libs.json.Writes

sealed trait TableFileType {
  val stringValue: String
}

object TableFileType {

  case object JSON extends TableFileType {
    override val stringValue: String = "JSON"
  }

  case object CSV extends TableFileType {
    override val stringValue: String = "CSV"
  }

  implicit val TableFileTypeWrites: Writes[TableFileType] = implicitly[Writes[String]].contramap(_.stringValue)

}

