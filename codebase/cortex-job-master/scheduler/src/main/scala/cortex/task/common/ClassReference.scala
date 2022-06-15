package cortex.task.common

import cortex.JsonSupport.SnakeJson
import play.api.libs.json.OWrites

case class ClassReference(
    packageLocation: Option[String],
    moduleName:      String,
    className:       String
)

object ClassReference {
  implicit val classReferenceWrites: OWrites[ClassReference] = SnakeJson.writes[ClassReference]
}
