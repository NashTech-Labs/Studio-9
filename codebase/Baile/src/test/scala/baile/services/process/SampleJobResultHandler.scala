package baile.services.process

import java.util.UUID

import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import play.api.libs.json.Reads

import scala.concurrent.{ ExecutionContext, Future }

class SampleJobResultHandler(dependency: SampleJobResultHandlerDependency) extends JobResultHandler[SampleJobMeta] {

  override protected val metaReads: Reads[SampleJobMeta] = SampleJobMeta.SampleJobMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: SampleJobMeta
  )(implicit ec: ExecutionContext): Future[Unit] =
    if (lastStatus == CortexJobStatus.Completed) Future.successful(())
    else Future.failed(new RuntimeException(s"Unexpected status $lastStatus"))

  override protected def handleException(meta: SampleJobMeta): Future[Unit] = Future.unit
}

class SampleJobResultHandlerDependency
