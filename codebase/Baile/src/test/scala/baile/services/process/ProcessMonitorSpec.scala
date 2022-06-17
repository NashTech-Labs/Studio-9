package baile.services.process

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import baile.ExtendedBaseSpec
import baile.dao.process.ProcessDao
import baile.daocommons.WithId
import baile.domain.job.{ CortexJobProgress, CortexJobStatus }
import baile.domain.process.{ Process, ProcessStatus }
import baile.services.cortex.datacontract.CortexErrorDetails
import baile.services.cortex.job.CortexJobService
import baile.services.process.ProcessMonitor._
import baile.services.process.util.TestData._
import baile.RandomGenerators.randomString

import scala.concurrent.Future
import scala.concurrent.duration._

class ProcessMonitorSpec extends ExtendedBaseSpec {

  trait Setup {
    val processDao = mock[ProcessDao]
    val cortexJobService = mock[CortexJobService]
    
    val jobId = UUID.randomUUID()

    def doAfterInstantiate[T](f: ActorRef => Future[T]): T =
      doWithActor(
        ProcessMonitor.props(
          processDao = processDao,
          cortexJobService = cortexJobService,
          readStoredProcesses = true,
          processCheckInterval = processPollingInterval,
          startupRequestTimeout = processPollingInterval,
          handlersDependencySources = new SampleDependencyProvider
        )
      )(f).futureValue

    processDao.listAll(*, *)(*) shouldReturn future(Seq.empty)
  }

  val processPollingInterval = 150.milliseconds

  "ProcessMonitor" should {

    "be instantiated as an actor" in new Setup {
      doAfterInstantiate(_ => future(()))
    }

    "start new process" in new Setup {
      processDao.create(*[Process])(*) shouldReturn future("id")

      doAfterInstantiate { monitor =>
        (monitor ? StartProcess(
          jobId,
          OwnerId,
          None,
          TargetId,
          TargetType,
          classOf[SampleJobResultHandler],
          SerializedJobMeta
        )).mapTo[WithId[Process]].map(_.entity.jobId shouldBe jobId)
      }
    }

    "return current process by id" in new Setup {
      processDao.create(*[Process])(*) shouldReturn future("id")

      doAfterInstantiate { monitor =>
        for {
          createdProcess <- (monitor ? StartProcess(
            jobId,
            OwnerId,
            None,
            TargetId,
            TargetType,
            classOf[SampleJobResultHandler],
            SerializedJobMeta
          )).mapTo[WithId[Process]]
          returnedProcess <- (monitor ? GetProcessById(createdProcess.id)).mapTo[Option[WithId[Process]]]
        } yield returnedProcess shouldBe Some(createdProcess)
      }
    }

    "return current process by target" in new Setup {
      processDao.create(*[Process])(*) shouldReturn future("id")

      doAfterInstantiate { monitor =>
        for {
          createdProcess <- (monitor ? StartProcess(
            jobId,
            OwnerId,
            None,
            TargetId,
            TargetType,
            classOf[SampleJobResultHandler],
            SerializedJobMeta
          )).mapTo[WithId[Process]]
          returnedProcess <- (monitor ? GetProcessByTargetAndHandler(
            TargetId,
            TargetType,
            None
          )).mapTo[Option[WithId[Process]]]
        } yield returnedProcess shouldBe Some(createdProcess)
      }
    }

    "return current process by target and handler" in new Setup {
      processDao.create(*[Process])(*) shouldReturn future("id")

      doAfterInstantiate { monitor =>
        for {
          createdProcess <- (monitor ? StartProcess(
            jobId,
            OwnerId,
            None,
            TargetId,
            TargetType,
            classOf[SampleJobResultHandler],
            SerializedJobMeta
          )).mapTo[WithId[Process]]
          returnedProcess <- (monitor ? GetProcessByTargetAndHandler(
            TargetId,
            TargetType,
            Some(classOf[SampleJobResultHandler])
          )).mapTo[Option[WithId[Process]]]
        } yield returnedProcess shouldBe Some(createdProcess)
      }
    }

    "return current processes by target" in new Setup {
      processDao.create(*[Process])(*) shouldReturn future("id")

      doAfterInstantiate { monitor =>
        for {
          createdProcess <- (monitor ? StartProcess(
            jobId,
            OwnerId,
            None,
            TargetId,
            TargetType,
            classOf[SampleJobResultHandler],
            SerializedJobMeta
          )).mapTo[WithId[Process]]
          returnedProcess <- (monitor ? GetProcessesByTargetAndHandler(
            TargetId,
            TargetType,
            None
          )).mapTo[Seq[WithId[Process]]]
        } yield returnedProcess shouldBe Seq(createdProcess)
      }
    }

    "update process info" in new Setup {
      processDao.create(*[Process])(*) shouldReturn future("id")
      cortexJobService.getJobProgress(jobId) shouldReturn future(
        CortexJobProgress(jobId, CortexJobStatus.Running, Some(0.2), None, None)
      )
      processDao.update(*, *)(*) shouldReturn future(None)

      doAfterInstantiate { monitor =>
        for {
          WithId(process, _) <- (monitor ? StartProcess(
            jobId,
            OwnerId,
            None,
            TargetId,
            TargetType,
            classOf[SampleJobResultHandler],
            SerializedJobMeta
          )).mapTo[WithId[Process]]
          _ = Thread.sleep((processPollingInterval * 10).toMillis)
          result <- (monitor ? GetProcessByTargetAndHandler(
            process.targetId,
            process.targetType,
            None
          )).mapTo[Option[WithId[Process]]]
        } yield {
          val updatedProcess = result.get.entity
          updatedProcess.started shouldBe defined
          updatedProcess.copy(started = None) shouldBe process.copy(
            progress = Some(0.2),
            status = ProcessStatus.Running
          )
        }
      }
    }

    "launch result handler after process finishes and delete process from memory" in new Setup {
      processDao.create(*[Process])(*) shouldReturn future("id")
      cortexJobService.getJobProgress(jobId) shouldReturn future(
        CortexJobProgress(jobId, CortexJobStatus.Completed, Some(1), None, None)
      )
      processDao.update(*, *)(*) shouldReturn future(None)

      doAfterInstantiate { monitor =>
        for {
          WithId(process, _) <- (monitor ? StartProcess(
            jobId,
            OwnerId,
            None,
            TargetId,
            TargetType,
            classOf[SampleJobResultHandler],
            SerializedJobMeta
          )).mapTo[WithId[Process]]
          _ = Thread.sleep((processPollingInterval * 10).toMillis)
          result <- (monitor ? GetProcessByTargetAndHandler(process.targetId,
            process.targetType,
            None
          )).mapTo[Option[Process]]
        } yield result shouldBe empty
      }
    }

    "delete process from memory if result handler will throw an exception" in new Setup {
      processDao.create(*[Process])(*) shouldReturn future("id")

      val cortexErrorDetails = CortexErrorDetails("error-code", "error-message", Map("stackTrace" -> "error"))
      cortexJobService.getJobProgress(jobId) shouldReturn future(
        CortexJobProgress(jobId, CortexJobStatus.Failed, Some(1), None, Some(cortexErrorDetails))
      )
      processDao.update(*, *)(*) shouldReturn future(None)
      doAfterInstantiate { monitor =>
        for {
          WithId(process, _) <- (monitor ? StartProcess(
            jobId,
            OwnerId,
            None,
            TargetId,
            TargetType,
            classOf[SampleJobResultHandler],
            SerializedJobMeta
          )).mapTo[WithId[Process]]
          _ = Thread.sleep((processPollingInterval * 10).toMillis)
          result <- (monitor ? GetProcessByTargetAndHandler(
            process.targetId,
            process.targetType,
            None
          )).mapTo[Option[Process]]
        } yield result shouldBe empty
      }
    }

    "cancel process" in new Setup {
      processDao.create(*[Process])(*) shouldReturn future("id")
      cortexJobService.cancelJob(jobId) shouldReturn Future.unit

      doAfterInstantiate { monitor =>
        for {
          WithId(_, processId) <- monitor ? StartProcess(
            jobId,
            OwnerId,
            None,
            TargetId,
            TargetType,
            classOf[SampleJobResultHandler],
            SerializedJobMeta
          )
          result <- (monitor ? CancelProcess(processId)).mapTo[Unit]
        } yield result
      }
    }

    "do nothing when asked to cancel unknown process" in new Setup {
      doAfterInstantiate(monitor => (monitor ? CancelProcess(randomString())).mapTo[Unit])
    }

  }

}
