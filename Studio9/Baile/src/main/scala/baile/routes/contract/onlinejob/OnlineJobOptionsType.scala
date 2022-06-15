package baile.routes.contract.onlinejob

import baile.utils.json.EnumFormatBuilder
import play.api.libs.json._

sealed trait OnlineJobOptionsType

object OnlineJobOptionsType {

  case object OnlineCvPrediction extends OnlineJobOptionsType

  implicit val OnlineJobOptionsTypeFormat: Format[OnlineJobOptionsType] = EnumFormatBuilder.build(
    {
      case "ONLINE_CV_PREDICTION" => OnlineJobOptionsType.OnlineCvPrediction
    },
    {
      case OnlineJobOptionsType.OnlineCvPrediction => "ONLINE_CV_PREDICTION"
    },
    "online job options type"
  )

}
