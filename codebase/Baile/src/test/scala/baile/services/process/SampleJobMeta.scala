package baile.services.process

import play.api.libs.json.{ Json, OFormat }

case class SampleJobMeta(data: List[Int])

object SampleJobMeta {

  implicit val SampleJobMetaFormat: OFormat[SampleJobMeta] = Json.format[SampleJobMeta]

}
