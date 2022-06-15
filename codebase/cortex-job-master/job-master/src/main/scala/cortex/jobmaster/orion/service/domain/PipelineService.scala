package cortex.jobmaster.orion.service.domain

import cortex.api.job.JobRequest
import cortex.api.job.JobType.Pipeline
import cortex.api.job.pipeline.PipelineRunResponse
import cortex.io.S3Client
import cortex.jobmaster.jobs.job.pipeline_runner.PipelineRunnerJob
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler.{ JobId, JobResult }
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.pipeline_runner.PipelineRunnerModule
import cortex.task.pipeline_runner.PipelineRunnerParams.PipelineRunnerTaskParams

import scala.concurrent.ExecutionContext

class PipelineService(
    pipelineRunnerJob: PipelineRunnerJob,
    s3AccessParams:    S3AccessParams,
    s3Client:          S3Client,
    requestFilesPath:  String,
    baileUrl:          String,
    sqlServerUrl:      String
)(implicit executionContext: ExecutionContext)
  extends JobRequestPartialHandler {

  override def handlePartial: PartialFunction[(JobId, JobRequest), JobResult] = {
    case (jobId, jobReq) if jobReq.`type` == Pipeline =>
      val params = PipelineRunnerTaskParams(
        requestPath    = s"$requestFilesPath/$jobId/request.pb",
        s3AccessParams = s3AccessParams,
        baileUrl       = baileUrl,
        sqlServerUrl   = sqlServerUrl
      )
      s3Client.put(
        bucket   = s3AccessParams.bucket,
        filename = params.requestPath,
        payload  = jobReq.payload.toByteArray
      )

      for {
        taskResult <- pipelineRunnerJob.runPipeline(jobId, params)
      } yield {
        (PipelineRunResponse.parseFrom(taskResult.bytes), JobTimeInfo(Seq(taskResult.taskTimeInfo)))
      }
  }
}

object PipelineService {

  def apply(
    scheduler:      TaskScheduler,
    s3AccessParams: S3AccessParams,
    s3Client:       S3Client,
    settings:       SettingsModule
  )(implicit executionContext: ExecutionContext): PipelineService = {

    val modelImportModule = new PipelineRunnerModule
    val pipelineRunnerJob = new PipelineRunnerJob(
      scheduler = scheduler,
      module    = modelImportModule,
      config    = settings.pipelineRunnerConfig
    )

    new PipelineService(
      pipelineRunnerJob = pipelineRunnerJob,
      s3AccessParams    = s3AccessParams,
      s3Client          = s3Client,
      requestFilesPath  = settings.taskRpcPath,
      baileUrl          = settings.baileUrl,
      sqlServerUrl      = settings.sqlServerUrl
    )
  }

}
