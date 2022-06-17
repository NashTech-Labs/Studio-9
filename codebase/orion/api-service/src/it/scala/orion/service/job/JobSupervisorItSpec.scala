package orion.service.job

import java.util.{ Date, UUID }

import akka.actor.{ ActorRef, Props }
import akka.persistence.fsm.PersistentFSM.{ CurrentState, SubscribeTransitionCallBack, Transition }
import akka.testkit.TestProbe
import cortex.api.job.message._
import mesosphere.marathon.client.model.v2.App
import orion.common.json4s.OrionJson4sSupport
import orion.domain.service.job.{ JobStatus, _ }
import orion.ipc.rabbitmq.MlJobTopology._
import orion.service.job.JobMasterAppWorker.{ CreateJobMasterApp, JobMasterAppCreated }
import orion.testkit.service.{ PersistenceSupport, RabbitMqItSupport, ServiceBaseSpec }

import scala.concurrent.duration._

class JobSupervisorItSpec extends ServiceBaseSpec with PersistenceSupport with RabbitMqItSupport {

  import JobSupervisor._

  // Fixtures
  val jobType = "TRAIN"
  val inputPath = "some/input/path"
  val outputPath = "some/output/path"
  val completedAt = new Date().withoutMillis()
  val tasksTimeInfo: Seq[TaskTimeInfo] = Seq(
    TaskTimeInfo("task1", TimeInfo(new Date().withoutMillis(), Some(new Date().withoutMillis()), None)),
    TaskTimeInfo("task2", TimeInfo(new Date().withoutMillis(), Some(new Date().withoutMillis()), None))
  )
  val tasksQueuedTime = 20 minutes
  val jobMasterApp = new App()
  val created = new Date().withoutMillis()
  val currentProgress = 0.1D
  val estimatedTimeRemaining = 2 hours


  trait Scope extends ServiceScope with OrionJson4sSupport {
    val jobId = UUID.randomUUID()

    val stateProbe = TestProbe()
    val jobMasterAppWorkerProbe = TestProbe()

    val service = system.actorOf(Props(new JobSupervisor {
      override def jobMasterAppWorker: ActorRef = jobMasterAppWorkerProbe.ref
    }))

  }

  "When in Idle state and receiving a SubmitJob msg, the JobSupervisor" should {
    "update its state to WaitingForJobMasterAppCreation and submit the Job for execution" in new Scope {
      // Initial state
      service ! SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsg(CurrentState(service, Idle, None))

      // Send Submit Job msg
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg

      stateProbe.expectMsg(Transition(service, Idle, WaitingForSubmissionMsgPublishing, None))
      stateProbe.expectMsg(Transition(service, WaitingForSubmissionMsgPublishing, WaitingForJobMasterAppCreation, None))

      // Verify msg has been published to RabbitMq
      getMessage[JobMessage](JobMasterInQueueTemplate.format(jobId)).futureValue shouldBe jobSubmissionMsg
    }
  }

  "When in WaitingForExecutionStart state and receiving a Heartbeat msg, the JobSupervisor" should {
    "update its state to Running, publish a job started event and change its state to WaitingForResult" in new Scope {
      // Initial state
      service ! SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsg(CurrentState(service, Idle, None))

      // Send Submit Job msg
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg

      stateProbe.expectMsg(Transition(service, Idle, WaitingForSubmissionMsgPublishing, None))
      stateProbe.expectMsg(Transition(service, WaitingForSubmissionMsgPublishing, WaitingForJobMasterAppCreation, None))

      // Verify msg has been published to RabbitMq
      getMessage[JobMessage](JobMasterInQueueTemplate.format(jobId)).futureValue shouldBe jobSubmissionMsg

      jobMasterAppWorkerProbe.expectMsg(CreateJobMasterApp(jobId))
      jobMasterAppWorkerProbe.reply(JobMasterAppCreated(jobMasterApp))

      stateProbe.expectMsg(Transition(service, WaitingForJobMasterAppCreation, WaitingForExecutionStart, None))

      // Send Heartbeat msg
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      service ! heartbeatMsg
      stateProbe.expectMsg(Transition(service, WaitingForExecutionStart, WaitingForJobStartedMsgPublishing, None))
      stateProbe.expectMsg(Transition(service, WaitingForJobStartedMsgPublishing, WaitingForExecutionResult, None))

      // Verify msg has been published to RabbitMq
      val jobStartedMsg = JobMessage(JobMessageMeta(jobId), JobStarted(created))
      getMessage[JobMessage](JobStatusQueue).futureValue shouldBe jobStartedMsg

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      expectMsg(JobStatus.Running)
    }
  }

  "When in WaitingForResult state and receiving a Heartbeat msg, the JobSupervisor" should {
    "stay in same state and publish Heartbeat msg to JobStatus queue" in new Scope {
      // Initial state
      service ! SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsg(CurrentState(service, Idle, None))

      // Send Submit Job msg
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg

      stateProbe.expectMsg(Transition(service, Idle, WaitingForSubmissionMsgPublishing, None))
      stateProbe.expectMsg(Transition(service, WaitingForSubmissionMsgPublishing, WaitingForJobMasterAppCreation, None))

      // Verify msg has been published to RabbitMq
      getMessage[JobMessage](JobMasterInQueueTemplate.format(jobId)).futureValue shouldBe jobSubmissionMsg

      jobMasterAppWorkerProbe.expectMsg(CreateJobMasterApp(jobId))
      jobMasterAppWorkerProbe.reply(JobMasterAppCreated(jobMasterApp))

      stateProbe.expectMsg(Transition(service, WaitingForJobMasterAppCreation, WaitingForExecutionStart, None))

      // Send Heartbeat msg
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      service ! heartbeatMsg
      stateProbe.expectMsg(Transition(service, WaitingForExecutionStart, WaitingForJobStartedMsgPublishing, None))
      stateProbe.expectMsg(Transition(service, WaitingForJobStartedMsgPublishing, WaitingForExecutionResult, None))

      // Verify msg has been published to RabbitMq
      val jobStartedMsg = JobMessage(JobMessageMeta(jobId), JobStarted(created))
      getMessage[JobMessage](JobStatusQueue).futureValue shouldBe jobStartedMsg

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      expectMsg(JobStatus.Running)

      // Send Heartbeat msg
      service ! heartbeatMsg

      // Verify msg has been published to RabbitMq
      getMessage[JobMessage](JobStatusQueue).futureValue shouldBe heartbeatMsg
    }
  }

  "When in WaitingForResult state and receiving a JobResultSuccess msg, the JobSupervisor" should {
    "update its state to JobCompleted, notify job result and stop itself" in new Scope {
      // Initial state
      service ! SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsg(CurrentState(service, Idle, None))

      // Send Submit Job msg
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg

      stateProbe.expectMsg(Transition(service, Idle, WaitingForSubmissionMsgPublishing, None))
      stateProbe.expectMsg(Transition(service, WaitingForSubmissionMsgPublishing, WaitingForJobMasterAppCreation, None))

      // Verify msg has been published to RabbitMq
      getMessage[JobMessage](JobMasterInQueueTemplate.format(jobId)).futureValue shouldBe jobSubmissionMsg

      jobMasterAppWorkerProbe.expectMsg(CreateJobMasterApp(jobId))
      jobMasterAppWorkerProbe.reply(JobMasterAppCreated(jobMasterApp))

      stateProbe.expectMsg(Transition(service, WaitingForJobMasterAppCreation, WaitingForExecutionStart, None))

      // Send Heartbeat msg
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      service ! heartbeatMsg
      stateProbe.expectMsg(Transition(service, WaitingForExecutionStart, WaitingForJobStartedMsgPublishing, None))
      stateProbe.expectMsg(Transition(service, WaitingForJobStartedMsgPublishing, WaitingForExecutionResult, None))

      // Verify msg has been published to RabbitMq
      val jobStartedMsg = JobMessage(JobMessageMeta(jobId), JobStarted(created))
      getMessage[JobMessage](JobStatusQueue).futureValue shouldBe jobStartedMsg

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      expectMsg(JobStatus.Running)

      // Send Job Result msg
      watch(service)
      val jobResultMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), JobResultSuccess(completedAt, tasksTimeInfo, tasksQueuedTime, outputPath))
      service ! jobResultMsg
      stateProbe.expectMsg(Transition(service, WaitingForExecutionResult, WaitingForResultMsgPublishing, None))

      stateProbe.expectMsg(Transition(service, WaitingForResultMsgPublishing, WaitingForResourcesCleanUpMsgPublishing, None))
      // Verify result msg has been published to RabbitMq
      getMessage[JobMessage](JobStatusQueue).futureValue shouldBe jobResultMsg

      expectTerminated(service)

      // Verify clean up msg has been published to RabbitMq
      val cleanUpResourcesMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CleanUpResources)
      getMessage[JobMessage](CleanUpResourcesQueue).futureValue shouldBe cleanUpResourcesMsg
    }
  }

  "When in WaitingForResult state and receiving a JobResultFailure msg, the JobSupervisor" should {
    "update its state to JobCompleted, notify job result and stop itself" in new Scope {
      // Initial state
      service ! SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsg(CurrentState(service, Idle, None))

      // Send Submit Job msg
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg

      stateProbe.expectMsg(Transition(service, Idle, WaitingForSubmissionMsgPublishing, None))
      stateProbe.expectMsg(Transition(service, WaitingForSubmissionMsgPublishing, WaitingForJobMasterAppCreation, None))

      // Verify msg has been published to RabbitMq
      getMessage[JobMessage](JobMasterInQueueTemplate.format(jobId)).futureValue shouldBe jobSubmissionMsg

      jobMasterAppWorkerProbe.expectMsg(CreateJobMasterApp(jobId))
      jobMasterAppWorkerProbe.reply(JobMasterAppCreated(jobMasterApp))

      stateProbe.expectMsg(Transition(service, WaitingForJobMasterAppCreation, WaitingForExecutionStart, None))

      // Send Heartbeat msg
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      service ! heartbeatMsg
      stateProbe.expectMsg(Transition(service, WaitingForExecutionStart, WaitingForJobStartedMsgPublishing, None))
      stateProbe.expectMsg(Transition(service, WaitingForJobStartedMsgPublishing, WaitingForExecutionResult, None))

      // Verify msg has been published to RabbitMq
      val jobStartedMsg = JobMessage(JobMessageMeta(jobId), JobStarted(created))
      getMessage[JobMessage](JobStatusQueue).futureValue shouldBe jobStartedMsg

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      expectMsg(JobStatus.Running)

      // Send Job Result msg
      watch(service)
      val jobResultMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), JobResultFailure(completedAt, outputPath, "Some error message"))
      service ! jobResultMsg
      stateProbe.expectMsg(Transition(service, WaitingForExecutionResult, WaitingForResultMsgPublishing, None))

      stateProbe.expectMsg(Transition(service, WaitingForResultMsgPublishing, WaitingForResourcesCleanUpMsgPublishing, None))
      // Verify result msg has been published to RabbitMq
      getMessage[JobMessage](JobStatusQueue).futureValue shouldBe jobResultMsg

      expectTerminated(service)

      // Verify clean up msg has been published to RabbitMq
      val cleanUpResourcesMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CleanUpResources)
      getMessage[JobMessage](CleanUpResourcesQueue).futureValue shouldBe cleanUpResourcesMsg
    }
  }

}
