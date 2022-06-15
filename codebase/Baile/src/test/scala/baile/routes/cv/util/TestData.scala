package baile.routes.cv.util

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.cv.prediction.{ CVPrediction, CVPredictionStatus }
import baile.services.usermanagement.util.TestData.SampleUser
import play.api.libs.json.{ JsObject, JsString, Json }

object TestData {

  val DateTime: Instant = Instant.now()

  val CVPredictionEntity = CVPrediction(
    ownerId = SampleUser.id,
    name = "name",
    status = CVPredictionStatus.Running,
    created = DateTime,
    updated = DateTime,
    modelId = "modelId",
    inputAlbumId = "input",
    outputAlbumId = "output",
    probabilityPredictionTableId = None,
    predictionTimeSpentSummary = None,
    evaluateTimeSpentSummary = None,
    description = None,
    evaluationSummary = None,
    cvModelPredictOptions = None
  )

  val CVPredictionWithIdEntity = WithId(CVPredictionEntity, "id")
  val CVPredictionResponseData: JsObject = Json.obj(
    "id" -> JsString("id"),
    "ownerId" -> JsString(CVPredictionEntity.ownerId.toString),
    "name" -> JsString(CVPredictionEntity.name),
    "status" -> JsString("RUNNING"),
    "created" -> JsString(DateTime.toString),
    "updated" -> JsString(DateTime.toString),
    "modelId" -> JsString(CVPredictionEntity.modelId),
    "input" -> JsString(CVPredictionEntity.inputAlbumId),
    "output" -> JsString(CVPredictionEntity.outputAlbumId)
  )
}
