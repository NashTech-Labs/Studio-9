package cortex.jobmaster.jobs.job.dataset

import cortex.CortexException
import cortex.common.future.FutureExtensions._
import cortex.common.Logging
import cortex.io.S3Client
import cortex.common.Utils
import cortex.common.logging.JMLoggerFactory
import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.jobmaster.jobs.job.FileSource
import cortex.jobmaster.jobs.job.dataset.DatasetTransferJob.{ DatasetTransferJobResult, UploadedFile }
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.dataset.DatasetTransferModule
import cortex.task.dataset.DatasetTransferParams.{ DatasetTransferTaskParams, DatasetTransferTaskResult, FailedFile }
import cortex.task.task_creators.GenericTask

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class DatasetTransferJob(
    scheduler: TaskScheduler,
    module:    DatasetTransferModule,
    config:    DatasetTransferConfig
)(implicit val context: ExecutionContext, val loggerFactory: JMLoggerFactory) extends TaskIdGenerator with Logging {

  def transferDataset(
    jobId:             String,
    sourcePath:        String,
    targetPrefix:      String,
    fileSource:        FileSource[TransferFile],
    inS3AccessParams:  S3AccessParams,
    outS3AccessParams: S3AccessParams
  ): Future[(DatasetTransferJobResult, JobTimeInfo.TasksTimeInfo)] = {

    def prepareTasks(baseRelativePath: Option[String], datasetFiles: Seq[TransferFile]) = {
      Try {
        val tasks = createTasks(
          jobId,
          sourcePath,
          targetPrefix,
          baseRelativePath,
          inS3AccessParams,
          outS3AccessParams,
          datasetFiles
        )
        val tasksSizes = tasks
          .map(x => s"{taskId: ${x.id}, " +
            s"num: ${x.getParams.filePaths.size}, " +
            s"mem: ${x.memory}}")
          .mkString(",")
        log.info(s"Created tasks number: ${tasks.length}, task sizes: $tasksSizes")
        tasks
      }
    }

    val result = for {
      (filesToUpload, failedFiles) <- Try(prepareFilesToTransfer(fileSource)).toFuture
      tasks <- prepareTasks(fileSource.baseRelativePath, filesToUpload).toFuture
      finishedTasks <- Future.sequence(tasks.map(scheduler.submitTask))
      s3OutClient <- Try(getS3Client(outS3AccessParams)).toFuture
    } yield {
      val actualFiles = s3OutClient.getFiles(
        bucket = outS3AccessParams.bucket,
        path   = Some(targetPrefix)
      ).map(file => file.copy(filepath = Utils.cutBasePath(targetPrefix, file.filepath)))
      val flattenSucceed = finishedTasks.flatMap(_.succeed)
      val flattenFailed = finishedTasks.flatMap(_.failed)
      val tasksTimeInfo = finishedTasks.map(_.taskTimeInfo)
      val uploadedFiles = flattenSucceed.map(x => {
        val filepath = x.path
        val fileSize = actualFiles.find(_.filepath == filepath)
          .getOrElse(throw new CortexException(s"can't find uploaded file: [${x.path}] to get file size"))
          .fileSizeInBytes

        UploadedFile(
          name = filepath,
          path = filepath,
          size = fileSize
        )
      })
      (DatasetTransferJobResult(
        succeed = uploadedFiles,
        failed  = failedFiles ++ flattenFailed
      ), tasksTimeInfo)
    }

    result
  }

  def createTasks(
    jobId:            String,
    sourcePath:       String,
    targetPrefix:     String,
    baseRelativePath: Option[String],
    inS3Credentials:  S3AccessParams,
    outS3Credentials: S3AccessParams,
    filesToTransfer:  Seq[TransferFile]
  ): Seq[GenericTask[DatasetTransferTaskResult, DatasetTransferTaskParams]] = {
    if (filesToTransfer.nonEmpty) {
      val groupSize = findGroupSize(filesToTransfer.size)

      filesToTransfer.map(_.filename).grouped(groupSize).map { filePaths =>
        DatasetTransferTaskParams(
          sourcePath         = sourcePath,
          filePaths          = filePaths,
          inputAccessParams  = inS3Credentials,
          outputAccessParams = outS3Credentials,
          targetPrefix       = targetPrefix
        )
      }.zipWithIndex.map {
        case (params, index) =>
          val task = module.transformTask(
            id       = genTaskId(jobId),
            jobId    = jobId,
            taskPath = s"$jobId/dataset_transfer_$index",
            params   = params,
            cpus     = config.cpus,
            memory   = config.memory
          )
          task.setAttempts(2)
          task
      }.toList
    } else {
      Seq.empty
    }
  }

  /**
   *
   * @param size                  initial group size
   * @param parallelizationFactor approximate amount of groups to create
   * @return
   */
  @scala.annotation.tailrec
  final def findGroupSize(size: Int, parallelizationFactor: Int = config.parallelizationFactor): Int = {
    if (parallelizationFactor <= 1) {
      size
    } else if (parallelizationFactor > size || size / parallelizationFactor < config.minGroupSize) {
      findGroupSize(size, parallelizationFactor - 1)
    } else {
      size / parallelizationFactor
    }
  }

  def prepareFilesToTransfer(source: FileSource[TransferFile]): (Seq[TransferFile], Seq[FailedFile]) = {
    val files: Seq[TransferFile] = source.getFiles
    val (properFiles, filtered) = files.partition(file => {
      file.fileSizeInBytes <= config.fileMaxSize * 1024L * 1024L
    })
    val failedFiles = filtered.map(x => FailedFile(x.filename, s"${x.filename} was rejected because it's " +
      s"allocates more than ${config.fileMaxSize} megabytes"))
    (properFiles, failedFiles)
  }

  protected def getS3Client(s3AccessCredentials: S3AccessParams): S3Client = {
    val s3Client = new S3Client(
      accessKey    = s3AccessCredentials.accessKey,
      secretKey    = s3AccessCredentials.secretKey,
      sessionToken = s3AccessCredentials.sessionToken,
      region       = s3AccessCredentials.region,
      endpointUrl  = s3AccessCredentials.endpointUrl
    )
    s3Client
  }
}

object DatasetTransferJob {

  case class DatasetTransferJobResult(
      succeed: Seq[UploadedFile],
      failed:  Seq[FailedFile]
  )

  case class UploadedFile(
      name: String,
      path: String,
      size: Long
  )

}
