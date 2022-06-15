package cortex.rest.job

import java.util.UUID

import akka.event.Logging
import akka.http.scaladsl.model.headers.HttpCredentials
import cortex.common.json4s.CortexJson4sSupport
import cortex.domain.rest.job.SubmitJobDataContract
import cortex.domain.service.job.{ JobEntity, JobStatus, JobStatusData }
import cortex.testkit.E2ESpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps


trait BaseJobE2EUtils extends CortexJson4sSupport {
  self: E2ESpec =>

  val cortexBaseUrl: String
  val credentials: HttpCredentials
  private val logger = Logging(system, this.getClass.getSimpleName)

  private def awaitCompletion(jobId: UUID): Unit = {
    val host = s"$cortexBaseUrl/jobs/${jobId.toString}/status"

    def pollStatus() = {
      val retrieveResponse = get(host, Some(credentials)).unwrapToOption[JobStatusData].futureValue
      assert(
        retrieveResponse.exists(_.status == JobStatus.Failed) ||
        retrieveResponse.exists(_.status == JobStatus.Completed) ||
        retrieveResponse.exists(_.status == JobStatus.Cancelled)
      )
    }

    awaitAssert(pollStatus(), 10 minutes, 5 seconds)
    val actualStatus = get(host, Some(credentials)).unwrapToOption[JobStatusData].futureValue.map(_.status)
    actualStatus should contain(JobStatus.Completed)
  }

  protected def submitJobAndAwaitCompletion(
    jobId: UUID,
    ownerId: UUID,
    inputJobPath: String,
    jobType: String
  ): Unit = {
    logger.info(s"submitting job with id - $jobId")
    val createJobData = SubmitJobDataContract(Some(jobId), ownerId, jobType, inputJobPath)
    val createResponse = post(s"$cortexBaseUrl/jobs", createJobData, Some(credentials)).unwrapTo[JobEntity].futureValue
    createResponse.id shouldBe jobId
    awaitCompletion(jobId)
  }
}
