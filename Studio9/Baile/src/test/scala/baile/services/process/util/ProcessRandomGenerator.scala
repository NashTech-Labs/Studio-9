package baile.services.process.util

import java.time.Instant
import java.util.UUID

import baile.RandomGenerators.{ randomString, randomOf }
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.process.{ Process, ProcessStatus, ResultHandlerMeta }
import play.api.libs.json.JsObject

object ProcessRandomGenerator {

  def randomProcess(
    ownerId: UUID = UUID.randomUUID(),
    targetId: String = randomString(),
    targetType: AssetType = randomOf(
      AssetType.Experiment,
      AssetType.CvModel,
      AssetType.CvPrediction,
      AssetType.DCProject,
      AssetType.Album,
      AssetType.OnlineJob,
      AssetType.Table,
      AssetType.TabularModel,
      AssetType.TabularPrediction
    ),
    authToken: Option[String] = randomOf(None, Some(randomString()))
  ): WithId[Process] = WithId(
    Process(
      targetId = targetId,
      targetType = targetType,
      ownerId = ownerId,
      authToken = authToken,
      jobId = UUID.randomUUID(),
      status = ProcessStatus.Queued,
      progress = None,
      estimatedTimeRemaining = None,
      created = Instant.now(),
      started = None,
      completed = None,
      errorCauseMessage = None,
      errorDetails = None,
      onComplete = ResultHandlerMeta(
        handlerClassName = randomString(),
        meta = JsObject.empty
      ),
      auxiliaryOnComplete = Seq.empty
    ),
    randomString()
  )

}
