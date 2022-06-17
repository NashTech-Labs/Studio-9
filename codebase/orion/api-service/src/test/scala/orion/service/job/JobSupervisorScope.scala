package orion.service.job

import java.util.UUID

import akka.actor.{ ActorRef, Props }
import akka.persistence.fsm.PersistentFSM.{ CurrentState, SubscribeTransitionCallBack, UnsubscribeTransitionCallBack }
import akka.testkit.TestProbe
import orion.common.json4s.OrionJson4sSupport
import orion.service.job.JobSupervisor.{ JobData, MessagePublishingEvent, MessagePublishingRetryEvent }
import orion.testkit.service.{ PersistenceSupport, ServiceBaseSpec }

import scala.concurrent.duration._

trait JobSupervisorScope extends ServiceBaseSpec with PersistenceSupport {

  case object GetJobData
  case object SetPublishRetries
  case object ResetPublishRetries

  trait Scope extends ServiceScope with OrionJson4sSupport {

    val jobId: UUID = UUID.randomUUID()

    val stateProbe = TestProbe()
    val jobMessagePublisherWorkerProbe = TestProbe()
    val jobMasterAppWorkerProbe = TestProbe()

    val service: ActorRef = createService()

    def createService(testPersistenceId: String = UUID.randomUUID().toString): ActorRef = system.actorOf(Props(new JobSupervisor with DateSupportTesting {
      override def persistenceId: String = testPersistenceId //to test persistence
      override def jobMessagePublisherWorker: ActorRef = jobMessagePublisherWorkerProbe.ref

      override def jobMasterAppWorker: ActorRef = jobMasterAppWorkerProbe.ref

      private val jobDataEvent: StateFunction = {
        case Event(GetJobData, job: JobData)        => stay replying job
        case Event(SetPublishRetries, job: JobData) => stay applying MessagePublishingEvent
        case Event(ResetPublishRetries, job: JobData) =>
          stay applying ((0 until job.publishRetriesLeft).map(_ => MessagePublishingRetryEvent): _*)
      }

      whenUnhandled {
        jobDataEvent.orElse(unhandled())
      }
    }))

    def assertCurrentState(state: JobSupervisor.State, service: ActorRef = service): Unit = {
      service ! SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsg(5.seconds, CurrentState(service, state, None))
    }

    def clearState(service: ActorRef = service): Unit = {
      service ! UnsubscribeTransitionCallBack(stateProbe.ref)
    }

    def setRetries(): Unit = {
      service ! SetPublishRetries
    }

    def clearRetries(): Unit = {
      service ! ResetPublishRetries
    }
  }
}
