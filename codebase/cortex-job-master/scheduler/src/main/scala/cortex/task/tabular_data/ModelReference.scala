package cortex.task.tabular_data

import cortex.JsonSupport.SnakeJson
import play.api.libs.json.Reads

case class ModelReference(
    id:   String,
    path: String
)

object ModelReference {

  implicit val ModelReferenceReads: Reads[ModelReference] = SnakeJson.reads[ModelReference]

}
