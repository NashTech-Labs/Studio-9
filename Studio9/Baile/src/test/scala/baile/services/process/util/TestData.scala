package baile.services.process.util

import java.time.Instant
import java.util.UUID

import baile.RandomGenerators
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.process.{ Process, ProcessStatus, ResultHandlerMeta }
import baile.services.process.SampleJobMeta
import baile.services.tabular.model.TabularModelTrainResultHandler
import baile.services.usermanagement.util.TestData.SampleUser
import play.api.libs.json.Json

object TestData {

  val JobId = UUID.randomUUID
  val TargetId = "tid"
  val TargetType = AssetType.Table
  val OwnerId = SampleUser.id
  val JobMeta = SampleJobMeta(List(23, 42))
  val SerializedJobMeta = Json.toJsObject(JobMeta)

  val SampleProcess = WithId(
    Process(
      targetId = TargetId,
      targetType = TargetType,
      ownerId = OwnerId,
      authToken = Some(RandomGenerators.randomString()),
      jobId = JobId,
      status = ProcessStatus.Running,
      progress = Some(0.5),
      estimatedTimeRemaining = None,
      created = Instant.now,
      started = None,
      completed = None,
      errorCauseMessage = None,
      errorDetails = None,
      onComplete = ResultHandlerMeta(
        handlerClassName = classOf[TabularModelTrainResultHandler].getCanonicalName,
        meta = SerializedJobMeta
      ),
      auxiliaryOnComplete = Seq.empty
    ),
    "pid"
  )

}
