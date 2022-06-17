package cortex.jobmaster.orion.service.domain.dataset

import cortex.api.job.JobType.{ S3DatasetExport, S3DatasetImport }
import cortex.api.job._
import cortex.api.job.common.{ FailedFile, File }
import cortex.api.job.dataset.{
  S3DatasetExportRequest,
  S3DatasetImportRequest,
  S3DatasetImportResponse,
  UploadedDatasetFile
}
import cortex.common.logging.JMLoggerFactory
import cortex.jobmaster.jobs.job.dataset.DatasetTransferJob
import cortex.jobmaster.jobs.job.dataset.TransferFileSource.S3FileSource
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler.{ JobId, JobResult }
import cortex.jobmaster.orion.service.domain.StringHelpers._
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.dataset.DatasetTransferModule

import scala.concurrent.{ ExecutionContext, Future }

/**
 * To transfer files from our s3 to an external s3 or vice versa
 */
class DatasetService(
    datasetTransferJob: DatasetTransferJob,
    s3AccessParams:     S3AccessParams
)(implicit val context: ExecutionContext) extends JobRequestPartialHandler {

  def importDataset(jobId: JobId, req: S3DatasetImportRequest): Future[(S3DatasetImportResponse, JobTimeInfo)] = {
    for {
      inS3AccessParams <- Future.successful(S3AccessParams(
        bucket       = req.bucketName,
        accessKey    = req.getAwsAccessKey,
        secretKey    = req.getAwsSecretKey,
        region       = req.awsRegion,
        sessionToken = req.awsSessionToken
      ))
      (datasetTransferResult, jobTasksTimeInfo) <- datasetTransferJob.transferDataset(
        jobId,
        req.datasetPath,
        req.targetPrefix,
        fileSource        = S3FileSource(
          inS3AccessParams,
          Some(req.datasetPath.removeTrailingSlashes()).filter(_.nonEmpty)
        ),
        inS3AccessParams  = inS3AccessParams,
        outS3AccessParams = s3AccessParams
      )
    } yield {
      val succeed = datasetTransferResult.succeed.map(dataset => {
        UploadedDatasetFile(
          file = Some(File(dataset.path, dataset.size, dataset.name))
        )
      })
      val failed = datasetTransferResult.failed.map(f => FailedFile(f.path, Some(f.reason)))
      (S3DatasetImportResponse(succeed, failed), JobTimeInfo(jobTasksTimeInfo))
    }
  }

  def exportDataset(jobId: JobId, req: S3DatasetExportRequest): Future[(S3DatasetImportResponse, JobTimeInfo)] = {
    for {
      outS3AccessParams <- Future.successful(S3AccessParams(
        bucket       = req.bucketName,
        accessKey    = req.getAwsAccessKey,
        secretKey    = req.getAwsSecretKey,
        region       = req.awsRegion,
        sessionToken = req.awsSessionToken
      ))
      (datasetTransferResult, jobTasksTimeInfo) <- datasetTransferJob.transferDataset(
        jobId,
        req.datasetPath,
        req.targetPrefix,
        fileSource        = S3FileSource(
          s3AccessParams,
          Some(req.datasetPath.removeTrailingSlashes()).filter(_.nonEmpty)
        ),
        inS3AccessParams  = s3AccessParams,
        outS3AccessParams = outS3AccessParams
      )
    } yield {
      val succeed = datasetTransferResult.succeed.map(uploadedFile => {
        UploadedDatasetFile(
          file = Some(File(uploadedFile.path, uploadedFile.size, uploadedFile.name))
        )
      })
      val failed = datasetTransferResult.failed.map(f => FailedFile(f.path, Some(f.reason)))
      (S3DatasetImportResponse(succeed, failed), JobTimeInfo(jobTasksTimeInfo))
    }
  }

  override def handlePartial: PartialFunction[(JobId, JobRequest), JobResult] = {
    case (jobId, jobReq) if jobReq.`type` == S3DatasetImport =>
      val importRequest = S3DatasetImportRequest.parseFrom(jobReq.payload.toByteArray)
      importDataset(jobId, importRequest)
    case (jobId, jobReq) if jobReq.`type` == S3DatasetExport =>
      val exportRequest = S3DatasetExportRequest.parseFrom(jobReq.payload.toByteArray)
      exportDataset(jobId, exportRequest)
  }

}

object DatasetService {

  def apply(
    scheduler:      TaskScheduler,
    s3AccessParams: S3AccessParams,
    settings:       SettingsModule
  )(implicit executionContext: ExecutionContext, loggerFactory: JMLoggerFactory): DatasetService = {
    val datasetTransferModule = new DatasetTransferModule()
    val datasetTransferJob = new DatasetTransferJob(
      scheduler = scheduler,
      module    = datasetTransferModule,
      config    = settings.datasetTransferConfig
    )

    new DatasetService(datasetTransferJob, s3AccessParams)
  }
}
