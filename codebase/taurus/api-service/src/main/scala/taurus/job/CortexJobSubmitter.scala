package taurus.job

import java.text.SimpleDateFormat
import java.util.{ Date, UUID }

import akka.actor.Props
import akka.pattern.pipe
import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import cortex.api.job.JobRequest
import cortex.api.job.online.prediction.{ Image, PredictRequest, PredictResponse }
import orion.ipc.common.withRetry
import taurus.common.json4s.TaurusJson4sSupport
import taurus.common.service._
import taurus.domain.service.job._
import taurus.job.BatchJobService.OnlinePredictionBatchJob
import taurus.job.CortexJobSubmitter.{ StartJob, WatchJob }
import taurus.common.utils.FutureExtensions._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success, Try }

object CortexJobSubmitter {

  def name(jobId: UUID): String = s"cortex-job-submitter-$jobId"

  def props(jobId: UUID): Props = {
    Props(new CortexJobSubmitter(jobId))
  }

  case class StartJob(batchJob: OnlinePredictionBatchJob)
  case object WatchJob
}

class CortexJobSubmitter(jobId: UUID) extends Service
    with S3ClientSupport
    with HttpClientSupport
    with TaurusJson4sSupport {

  log.debug("[ JobId: {} ] - Starting Job Submitter Worker", jobId)

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  val cortexSettings: CortexSettings = CortexSettings(context.system)
  val cortexJobsUrl: String = s"${cortexSettings.baseUrl}/jobs"
  val cortexJobStatusUrl: String = s"$cortexJobsUrl/$jobId/status"
  val cortexCredentials: BasicHttpCredentials = BasicHttpCredentials(cortexSettings.credentials.username, cortexSettings.credentials.password)
  val cortexRequestRetryCount: Int = cortexSettings.requestRetryCount
  val cortexJobPollingInterval: FiniteDuration = cortexSettings.jobPollingInterval
  val cortexJobPollingMaxAttempts: Int = cortexSettings.jobPollingMaxAttempts
  val cortexInputPath: String = {
    val date = new Date
    val dateStamp = new SimpleDateFormat("yyyyMMdd").format(date)
    s"${cortexSettings.baseInputPath}/$dateStamp/$jobId/params.dat"
  }

  val ariesSettings: AriesSettings = AriesSettings(context.system)
  val ariesJobUrl: String = s"${ariesSettings.baseUrl}/jobs/$jobId"
  val ariesCredentials: BasicHttpCredentials = BasicHttpCredentials(ariesSettings.credentials.username, ariesSettings.credentials.password)
  val ariesRequestRetryCount: Int = cortexSettings.requestRetryCount

  override val receive: Receive = {
    case StartJob(batchJob) =>

      def checkJobExistence(): Future[Unit] =
        loadJob().map(_.status).map {
          case StatusCodes.OK => throw new Exception(
            s"Job $jobId is already started in Cortex. You can only ask me to watch it now"
          )
          case _ => ()
        }

      val result =
        for {
          _ <- checkJobExistence()
          _ <- saveRequestInRepository(batchJob)
          _ <- submitJob(batchJob)
        } yield ()

      result pipeTo sender
    case WatchJob =>
      val result = for {
        _ <- pollJobEnd()
        result <- readResult()
      } yield result

      result pipeTo sender
  }

  private def saveRequestInRepository(batchJob: OnlinePredictionBatchJob): Future[JobRequest] = {
    val predictRequest = PredictRequest(
      bucketName      = batchJob.settings.bucketName,
      aWSRegion       = batchJob.settings.awsRegion,
      aWSAccessKey    = batchJob.settings.awsAccessKey,
      aWSSecretKey    = batchJob.settings.awsSecretKey,
      aWSSessionToken = batchJob.settings.awsSessionToken.getOrElse(""),
      images          = batchJob.records.map(record => Image(record.key, record.size)),
      targetPrefix    = batchJob.settings.targetPrefix,
      modelId         = batchJob.settings.modelId
    )

    val jobRequest = JobRequest(
      `type`  = cortex.api.job.JobType.OnlinePrediction,
      payload = predictRequest.toByteString
    )

    log.info("[ JobId: {} ] - [Save Job Data] - Saving Job data to remote repository.", jobId)

    val result = remoteRepository.write(cortexInputPath, jobRequest.toByteArray)

    result onComplete {
      case Success(_) => log.info("[ JobId: {} ] - [Save Job Data] - Saved job.", jobId)
      case Failure(e) => log.error("[ JobId: {} ] - [Save Job Data] - Failed to save job input data [{}] with error: [{}].", jobId, jobRequest, e)
    }

    result.map(_ => jobRequest)
  }

  private def submitJob(batchJob: OnlinePredictionBatchJob): Future[Unit] = {

    log.info("[ JobId: {} ] - [Submit Job] - Sending create request to Cortex REST API.", jobId)

    val result =
      for {
        ownerId <- Try(UUID.fromString(batchJob.settings.owner)).toFuture
        jobMessage = SubmitJobData(
          id        = Some(jobId),
          owner     = ownerId,
          jobType   = "ONLINE PREDICTION",
          inputPath = cortexInputPath
        )
        result <- withRetry(cortexRequestRetryCount)(httpClient.post(cortexJobsUrl, jobMessage, Some(cortexCredentials)))
      } yield {
        if (result.status != StatusCodes.Created)
          throw new Exception(s"Unexpected http response: $result")
        result
      }

    result andThen {
      case Success(_) => log.info("[ JobId: {} ] - [Submit Job] - Job submission succeeded.", jobId)
      case Failure(e) => log.error("[ JobId: {} ] - [Submit Job] - Failed to submit Job [{}] with error: [{}].", jobId, batchJob, e)
    } map (_ => ())
  }

  private def pollJobEnd(): Future[JobStatus] = {

    def failWithMessage(message: String) = Future.failed(new Exception(message))

    def checkStatus: Future[JobStatus] = withRetry(cortexRequestRetryCount)(
      httpClient.get(cortexJobStatusUrl, Some(cortexCredentials)).unwrapTo[JobStatusData]
    ).map(_.status)

    def poll(retriesLeft: Int): Future[JobStatus] = {
      import akka.pattern.after
      import JobStatus._

      def continue() =
        if (retriesLeft > 0) {
          after(cortexJobPollingInterval, context.system.scheduler)(poll(retriesLeft - 1))
        } else {
          log.info("[ JobId: {} ] - [Poll Job] - Stopped polling for job after {} attempts.", jobId, cortexJobPollingMaxAttempts)
          failWithMessage(s"Stopped polling after $cortexJobPollingMaxAttempts attempts.")
        }

      checkStatus.flatMap {
        case Completed =>
          log.info("[ JobId: {} ] - [Poll Job] - Job completed.", jobId)
          Future.successful(Completed)
        case Failed =>
          log.error("[ JobId: {} ] - [Poll Job] - Job failed.", jobId)
          failWithMessage(s"Job $jobId failed")
        case Cancelled =>
          //TODO should it be error or we just do nothing in this case?
          log.error("[ JobId: {} ] - [Poll Job] - Job canceled.", jobId)
          failWithMessage(s"Job $jobId canceled")
        case Submitted | Queued | Running =>
          continue()
        case _ =>
          failWithMessage("Unexpected status code")
      }
    }

    poll(cortexJobPollingMaxAttempts)

  }

  private def readResult(): Future[PredictResponse] = {

    def loadResultPath(): Future[String] =
      loadJob().unwrapTo[JobEntity].map(_.outputPath).map {
        case Some(outputPath) =>
          outputPath
        case None =>
          log.error("[ JobId: {} ] - [Read Job Result] - No outputPath.", jobId)
          throw new Exception("No outputPath")
      }

    def readResultFromRepository(resultPath: String) = {
      val result = remoteRepository.read(resultPath)

      result onComplete {
        case Success(_) => log.info("[ JobId: {} ] - [Read Job Result] - Read job result.", jobId)
        case Failure(e) => log.error("[ JobId: {} ] - [Read Job Result] - Failed to read job result with error: [{}].", jobId, e)
      }

      result
    }

    for {
      resultPath <- loadResultPath()
      result <- readResultFromRepository(resultPath)
    } yield PredictResponse.parseFrom(result)
  }

  private def loadJob(): Future[HttpResponse] =
    withRetry(ariesRequestRetryCount)(httpClient.get(ariesJobUrl, Some(ariesCredentials)))
}
