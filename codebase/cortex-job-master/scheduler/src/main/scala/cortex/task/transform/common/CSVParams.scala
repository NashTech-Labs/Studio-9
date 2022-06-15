package cortex.task.transform.common

import cortex.JsonSupport.SnakeJson
import play.api.libs.json.Writes

case class CSVParams(
    delimiter: String,
    nullValue: String
)

object CSVParams {

  implicit val CSVParamsWrites: Writes[CSVParams] = SnakeJson.writes[CSVParams]

}
