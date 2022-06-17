package cortex.task.project_packager

import play.api.libs.json._

sealed trait AssetType {
  val stringValue: String
}

object AssetType {

  case object TabularModel extends AssetType {
    override val stringValue: String = "TabularModel"
  }

  case object TabularPrediction extends AssetType {
    override val stringValue: String = "TabularPrediction"
  }

  case object Table extends AssetType {
    override val stringValue: String = "Table"
  }

  case object Flow extends AssetType {
    override val stringValue: String = "Flow"
  }

  case object Album extends AssetType {
    override val stringValue: String = "Album"
  }

  case object CvModel extends AssetType {
    override val stringValue: String = "CvModel"
  }

  case object CvPrediction extends AssetType {
    override val stringValue: String = "CvPrediction"
  }

  case object OnlineJob extends AssetType {
    override val stringValue: String = "OnlineJob"
  }

  case object DCProject extends AssetType {
    override val stringValue: String = "DCProject"
  }

  implicit object ColumnDataTypeReads extends Reads[AssetType] {
    override def reads(json: JsValue): JsResult[AssetType] = json match {
      case JsString(AssetType.TabularModel.stringValue) => JsSuccess(AssetType.TabularModel)
      case JsString(AssetType.TabularPrediction.stringValue) => JsSuccess(AssetType.TabularPrediction)
      case JsString(AssetType.Table.stringValue) => JsSuccess(AssetType.Table)
      case JsString(AssetType.Flow.stringValue) => JsSuccess(AssetType.Flow)
      case JsString(AssetType.Album.stringValue) => JsSuccess(AssetType.Album)
      case JsString(AssetType.CvModel.stringValue) => JsSuccess(AssetType.CvModel)
      case JsString(AssetType.CvPrediction.stringValue) => JsSuccess(AssetType.CvPrediction)
      case JsString(AssetType.OnlineJob.stringValue) => JsSuccess(AssetType.OnlineJob)
      case JsString(AssetType.DCProject.stringValue) => JsSuccess(AssetType.DCProject)
      case _ => JsError(s"Invalid AssetType: $json")
    }
  }
}
