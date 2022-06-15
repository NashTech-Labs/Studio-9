package baile.routes.contract.cv.model

import java.time.Instant

import baile.routes.contract.cv._
import baile.daocommons.WithId
import baile.domain.cv.model._
import play.api.libs.json.{ Json, OWrites }

case class CVModelResponse(
  id: String,
  ownerId: String,
  name: String,
  created: Instant,
  updated: Instant,
  status: CVModelStatus,
  modelType: CVModelType,
  classes: Option[Seq[String]],
  featureExtractorModelId: Option[String],
  description: Option[String],
  experimentId: Option[String],
  inLibrary: Boolean
)

object CVModelResponse {

  implicit val CVModelResponseWrites: OWrites[CVModelResponse] = Json.writes[CVModelResponse]

  def fromDomain(in: WithId[CVModel]): CVModelResponse = in match {
    case WithId(model, id) =>
      CVModelResponse(
        id = id,
        ownerId = model.ownerId.toString,
        name = model.name,
        created = model.created,
        updated = model.updated,
        status = model.status,
        modelType = CVModelType.fromDomain(model.`type`),
        classes = model.classNames,
        featureExtractorModelId = model.featureExtractorId,
        description = model.description,
        experimentId = model.experimentId,
        inLibrary = model.inLibrary
      )
  }

}
