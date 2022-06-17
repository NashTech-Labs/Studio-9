package taurus.job

import java.util.{ Date, UUID }

import akka.actor.Status
import akka.http.scaladsl.model.{ HttpEntity, HttpResponse, MediaTypes, StatusCodes }
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.testkit.TestActorRef
import cortex.api.job.JobRequest
import cortex.api.job.online.prediction.{ Image, LabledImage, PredictRequest, PredictResponse }
import taurus.common.json4s.{ Json, TaurusJson4sSupport }
import taurus.common.service.RemoteRepository.WriteResult
import taurus.domain.service.job._
import taurus.job.BatchJobService.{ OnlinePredictionBatchJob, OnlinePredictionBatchJobSettings }
import taurus.job.CortexJobSubmitter.{ StartJob, WatchJob }
import taurus.sqs.SQSMessagesProvider.S3Record
import taurus.testkit.service.ServiceBaseSpec

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class CortexJobSubmitterSpec extends ServiceBaseSpec {

  val jobId = UUID.randomUUID()

  val batchJob = OnlinePredictionBatchJob(
    settings = OnlinePredictionBatchJobSettings(
      streamId        = UUID.randomUUID.toString,
      owner           = UUID.randomUUID.toString,
      modelId         = UUID.randomUUID.toString,
      albumId         = UUID.randomUUID.toString,
      bucketName      = "dev.deepcortex.ai",
      awsRegion       = "US_EAST_1",
      awsAccessKey    = "accessKey",
      awsSecretKey    = "secretKey",
      awsSessionToken = None,
      targetPrefix    = "storage/images"
    ),
    records  = Seq(
      S3Record("image1.png", 20L),
      S3Record("image2.png", 30L),
      S3Record("image3.png", 40L),
      S3Record("image4.png", 50L)
    ),
    jobId    = jobId
  )

  val mockedCortexJobsUrl = "http://0.0.0.0:9000/v1/jobs"
  val mockedCortexJobStatusUrl = s"$mockedCortexJobsUrl/$jobId/status"
  val mockedCortexCredentials = BasicHttpCredentials("cortex-username", "cortex-password")
  val mockedCortexInputPath = s"~/test/taurus/jobs/$jobId/params.dat"
  val mockedCortexRequestRetryCount = 1
  val mockedCortexJobPollingInterval = 10 microseconds
  val mockedCortexJobPollingMaxAttempts = 1

  val mockedAriesJobUrl = s"http://0.0.0.0:9000/v1/jobs/$jobId"
  val mockedAriesCredentials = BasicHttpCredentials("cortex-username", "cortex-password")
  val mockedAriesRequestRetryCount = 1

  trait Scope extends ServiceScope with TaurusJson4sSupport {
    val service = TestActorRef(new CortexJobSubmitter(jobId) with RemoteRepositorySupportTesting with HttpClientSupportTesting {

      override val cortexJobsUrl = mockedCortexJobsUrl
      override val cortexJobStatusUrl = mockedCortexJobStatusUrl
      override val cortexCredentials = mockedCortexCredentials
      override val cortexInputPath = mockedCortexInputPath
      override val cortexRequestRetryCount = mockedCortexRequestRetryCount
      override val cortexJobPollingInterval = mockedCortexJobPollingInterval
      override val cortexJobPollingMaxAttempts = mockedCortexJobPollingMaxAttempts

      override val ariesJobUrl = mockedAriesJobUrl
      override val ariesCredentials = mockedAriesCredentials
      override val ariesRequestRetryCount = mockedAriesRequestRetryCount

    })
  }

  "When receiving a StartJob message, the CortexJobSubmitter" should {
    "save the msg data in the remote repository and create job in Cortex" in new Scope {
      mockHttpClient.getExpects(mockedAriesJobUrl, Some(mockedAriesCredentials))
        .returning(Future.successful(HttpResponse(StatusCodes.NotFound)))

      val predictRequest = PredictRequest(
        bucketName      = batchJob.settings.bucketName,
        aWSRegion       = batchJob.settings.awsRegion,
        aWSAccessKey    = batchJob.settings.awsAccessKey,
        aWSSecretKey    = batchJob.settings.awsSecretKey,
        aWSSessionToken = "",
        modelId         = batchJob.settings.modelId,
        images          = batchJob.records.map(record => Image(record.key, record.size)),
        targetPrefix    = batchJob.settings.targetPrefix
      )

      val jobRequest = JobRequest(
        `type`  = cortex.api.job.JobType.OnlinePrediction,
        payload = predictRequest.toByteString
      )
      mockRemoteRepository.writeExpects(mockedCortexInputPath, jobRequest.toByteArray)
        .returning(Future.successful(WriteResult))

      val submitJobData = SubmitJobData(
        Some(jobId),
        UUID.fromString(batchJob.settings.owner),
        "ONLINE PREDICTION",
        mockedCortexInputPath
      )
      mockHttpClient.postExpects(mockedCortexJobsUrl, submitJobData, Some(mockedCortexCredentials))
        .returning(Future.successful(HttpResponse(StatusCodes.Created)))

      watch(service)
      service ! StartJob(batchJob)
      expectMsg(())
    }

    "log an error if the saving of the msg data in the remote repository fails," +
      " send failure message and stop itself in" in new Scope {
        mockHttpClient.getExpects(mockedAriesJobUrl, Some(mockedAriesCredentials))
          .returning(Future.successful(HttpResponse(StatusCodes.NotFound)))
        val predictRequest = PredictRequest(
          bucketName      = batchJob.settings.bucketName,
          aWSRegion       = batchJob.settings.awsRegion,
          aWSAccessKey    = batchJob.settings.awsAccessKey,
          aWSSecretKey    = batchJob.settings.awsSecretKey,
          aWSSessionToken = "",
          modelId         = batchJob.settings.modelId,
          images          = batchJob.records.map(record => Image(record.key, record.size)),
          targetPrefix    = batchJob.settings.targetPrefix
        )

        val jobRequest = JobRequest(
          `type`  = cortex.api.job.JobType.OnlinePrediction,
          payload = predictRequest.toByteString
        )
        val remoteRepoException = new Exception("BOOM!")
        mockRemoteRepository.writeExpects(mockedCortexInputPath, jobRequest.toByteArray)
          .returning(Future.failed(remoteRepoException))

        watch(service)
        service ! StartJob(batchJob)
        expectMsg(Status.Failure(remoteRepoException))
      }
  }

  "When receiving WatchJob message, the CortexJobSubmitter" should {
    "send a request to Cortex, poll for results, read them and return them" +
      " if a job was started already and no errors were encountered" in new Scope {
        val jobStatusResponseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(JobStatusData(
          status                 = JobStatus.Completed,
          currentProgress        = None,
          estimatedTimeRemaining = None
        )))
        mockHttpClient.getExpects(mockedCortexJobStatusUrl, Some(mockedCortexCredentials))
          .returning(Future.successful(HttpResponse(status = StatusCodes.OK, entity = jobStatusResponseEntity)))

        val jobOutputPath = "~/results/prediction"
        val jobResponseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(JobEntity(
          id         = jobId,
          created    = new Date,
          owner      = UUID.fromString(batchJob.settings.owner),
          jobType    = "ONLINE PREDICTION",
          status     = JobStatus.Completed,
          inputPath  = mockedCortexInputPath,
          outputPath = Some(jobOutputPath)
        )))
        mockHttpClient.getExpects(mockedAriesJobUrl, Some(mockedAriesCredentials))
          .returning(Future.successful(HttpResponse(status = StatusCodes.OK, entity = jobResponseEntity)))

        val predictResponse = PredictResponse(
          images           = batchJob.records.map(record =>
            LabledImage(
              fileSize   = record.size,
              filePath   = record.key,
              label      = "tank",
              confidence = 1
            )),
          s3ResultsCsvPath = "results/data.csv"
        )
        mockRemoteRepository.readExpects(jobOutputPath)
          .returning(Future.successful(predictResponse.toByteArray))

        watch(service)
        service ! WatchJob
        expectMsg(predictResponse)
      }
  }

}
