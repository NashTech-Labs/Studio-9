package cortex.jobmaster.orion.service

import java.util.concurrent.TimeUnit
import java.util.{ Date, UUID }

import akka.actor.ActorSystem
import com.google.protobuf.ByteString
import com.trueaccord.scalapb.{ GeneratedMessage, GeneratedMessageCompanion }
import com.whisk.docker.scalatest.DockerTestKit
import cortex.api.job.JobRequest
import cortex.api.job.JobType.CVModelTrain
import cortex.api.job.computervision.CVModelTrainResult
import cortex.api.job.message._
import cortex.jobmaster.common.json4s.CortexJson4sSupport
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.orion.service.domain.{ JobRequestHandler, JobRequestPartialHandler }
import cortex.jobmaster.orion.service.io.{ BaseStorageFactory, StorageCleaner, StorageReader, StorageWriter }
import cortex.scheduler.{ SchedulerStoppedCortexException, SystemCortexException, TaskScheduler, UserCortexException }
import cortex.testkit.{ BaseSpec, DockerRabbitMqService, WithLogging }
import cortex.{ TaskTimeInfo => CortextTaskTimeInfo }
import org.mockito.Matchers.any
import org.scalamock.scalatest.MockFactory
import orion.ipc.rabbitmq.MlJobTopology._
import orion.ipc.rabbitmq.setup.Cluster
import orion.ipc.rabbitmq.setup.builders.MlJobTopologyBuilder

import scala.concurrent.duration.{ Duration, _ }
import scala.concurrent.{ Await, Future, Promise }

class OrionJobServiceAdapterSpec extends BaseSpec
  with DockerRabbitMqService
  with DockerTestKit
  with CortexJson4sSupport
  with MockFactory
  with WithLogging {

  val jobUuid = UUID.randomUUID()
  val jobId: String = jobUuid.toString
  val jobMasterInQueue: String = JobMasterInQueueTemplate.format(jobId)
  val jobMasterInRoutingKey: String = JobMasterInRoutingKeyTemplate.format(jobId)
  val jobMasterOutRoutingKey: String = JobMasterOutRoutingKeyTemplate.format(jobId)

  override def beforeAll(): Unit = {
    super.beforeAll()
    // build rabbit cluster topology
    Cluster(
      MlJobTopologyBuilder(),
      JobMasterTopologyBuilder(jobId)
    ).initialize()
  }

  "OrionJobServiceAdapter" should {
    "get job message from a specific queue on start" in {
      val jobMessage = JobMessage(
        JobMessageMeta(jobUuid)
      )

      implicit val actorSystem: ActorSystem = ActorSystem("JobServiceSpec")
      val rabbitMqService = new RabbitMqService(actorSystem)
      rabbitMqService.sendMessageToExchange(jobMessage, DataDistributorExchange, jobMasterInRoutingKey)

      val isHandled = Promise[Boolean]()

      val jobService = new OrionJobServiceAdapter(
        jobId              = jobId,
        rabbitMqService    = rabbitMqService,
        jobRequestHandler  = mock[JobRequestHandler],
        storageFactory     = mock[BaseStorageFactory],
        taskScheduler      = mock[TaskScheduler],
        storageCleaner     = mock[StorageCleaner],
        resourcesBasePaths = Seq(),
        heartbeatInterval  = any[Int]
      ) {
        override def handle(jobMessage: JobMessage): Unit = {
          isHandled.success(true)
        }
      }

      jobService.start()

      whenReady(isHandled.future) { result =>
        rabbitMqService.stop()
        actorSystem.terminate()
        Await.ready(actorSystem.whenTerminated, Duration(1, TimeUnit.MINUTES))
        result shouldBe true
      }
    }

    "handle job starting when receives SubmitJob request" in {
      val jobParamsPath = "hdfs://some/input/path"

      val jobMessage = JobMessage(
        JobMessageMeta(jobUuid, Some("TRAIN")),
        SubmitJob(jobParamsPath)
      )

      implicit val actorSystem: ActorSystem = ActorSystem("JobServiceSpec")
      val rabbitMqService = new RabbitMqService(actorSystem)
      rabbitMqService.sendMessageToExchange(jobMessage, DataDistributorExchange, jobMasterInRoutingKey)

      val trainParams = Promise[(UUID, String)]()

      val jobService = new OrionJobServiceAdapter(
        jobId              = jobId,
        rabbitMqService    = rabbitMqService,
        jobRequestHandler  = mock[JobRequestHandler],
        storageFactory     = mock[BaseStorageFactory],
        taskScheduler      = mock[TaskScheduler],
        storageCleaner     = mock[StorageCleaner],
        resourcesBasePaths = Seq(),
        heartbeatInterval  = any[Int]
      ) {
        override def startJob(jobId: UUID, path: String): Unit = {
          trainParams.success((jobId, path))
        }
      }
      jobService.start()

      whenReady(trainParams.future) { result =>
        rabbitMqService.stop()
        actorSystem.terminate()
        Await.ready(actorSystem.whenTerminated, Duration(1, TimeUnit.MINUTES))

        val (uuid, path) = result
        uuid should be(jobUuid)
        path should be(jobParamsPath)
      }
    }

    "on successful job completion send JobResultSuccess to DataDistributorExchange" in {
      val jobParamsPath = "hdfs://some/input/path"
      val jobMessage = JobMessage(
        JobMessageMeta(jobUuid, Some("TRAIN")),
        SubmitJob(jobParamsPath)
      )
      val jobRequest = JobRequest(CVModelTrain, ByteString.EMPTY)
      val modelTrainResult = CVModelTrainResult()
      val jobTimeInfo = JobTimeInfo(Seq(CortextTaskTimeInfo("task_id", new Date(), Some(new Date()), Some(new Date()))))

      implicit val actorSystem: ActorSystem = ActorSystem("JobServiceSpec")
      val rabbitMqService = new RabbitMqService(actorSystem)
      rabbitMqService.sendMessageToExchange(jobMessage, DataDistributorExchange, jobMasterInRoutingKey)

      val storageFactory = stub[BaseStorageFactory]
      val reader = stub[StorageReader[JobRequest]]
      val writer = stub[StorageWriter[GeneratedMessage]]
      val jobRequestHandler = stub[JobRequestHandler]

      (jobRequestHandler.handleJobRequest _).when((jobId, jobRequest)).returns(Future.successful((modelTrainResult, jobTimeInfo)))
      (reader.get _).when(jobParamsPath).returns(jobRequest)
      (writer.put _).when(modelTrainResult, jobId).returns(jobParamsPath)

      (storageFactory.createParamResultStorageWriter[GeneratedMessage] _).when().returns(writer)
      (storageFactory.createParamResultStorageReader[JobRequest]()(_: GeneratedMessageCompanion[JobRequest])).when(*).returns(reader)

      val jobService = new OrionJobServiceAdapter(
        jobId              = jobId,
        rabbitMqService    = rabbitMqService,
        jobRequestHandler  = jobRequestHandler,
        storageFactory     = storageFactory,
        taskScheduler      = mock[TaskScheduler],
        storageCleaner     = mock[StorageCleaner],
        resourcesBasePaths = Seq(),
        heartbeatInterval  = any[Int]
      ) {
        override protected def startHeartbeat(jobId: UUID, heartbeat: Int): Unit = ()
      }
      jobService.start()

      // check success job in the queue
      val jobResultId = Promise[(UUID, Seq[TaskTimeInfo], FiniteDuration)]()

      rabbitMqService.subscribe[JobMessage](JobMasterOutQueue, handle = {
        case JobMessage(JobMessageMeta(id, _), JobResultSuccess(_, tasksTimeInfo, tasksQueuedTime, _)) =>
          jobResultId.success((id, tasksTimeInfo, tasksQueuedTime))

        case _ => jobResultId.failure(new Exception("unexpected message"))
      })

      whenReady(jobResultId.future) {
        case (uuid, tasksTimeInfo, tasksQueuedTime) =>
          rabbitMqService.stop()
          actorSystem.terminate()
          Await.ready(actorSystem.whenTerminated, Duration(1, TimeUnit.MINUTES))
          uuid shouldEqual jobUuid
          tasksTimeInfo.foreach(taskTimeInfo => {
            jobTimeInfo.jobTasksTimeInfo.exists(_.taskId == taskTimeInfo.taskName) shouldBe true
          })
          tasksQueuedTime shouldEqual jobTimeInfo.jobTasksQueuedTime
      }
    }

    "on job failure send JobResultFailure to DataDistributorExchange" in {
      val jobParamsPath = "hdfs://some/input/path"
      val jobMessage = JobMessage(
        JobMessageMeta(jobUuid, Some("TRAIN")),
        SubmitJob(jobParamsPath)
      )
      val jobRequest = JobRequest(CVModelTrain, ByteString.EMPTY)

      implicit val actorSystem: ActorSystem = ActorSystem("JobServiceSpec")
      val rabbitMqService = new RabbitMqService(actorSystem)
      rabbitMqService.sendMessageToExchange(jobMessage, DataDistributorExchange, jobMasterInRoutingKey)

      val storageFactory = mock[BaseStorageFactory]
      val reader = stub[StorageReader[JobRequest]]
      val jobRequestHandler = stub[JobRequestHandler]

      (jobRequestHandler.handleJobRequest _).when((jobId, jobRequest)).returns(Future.failed(new Exception("")))
      (reader.get _).when(jobParamsPath).returns(jobRequest)

      (storageFactory.createParamResultStorageReader[JobRequest]()(_: GeneratedMessageCompanion[JobRequest]))
        .expects(*).returning(reader)

      val jobService = new OrionJobServiceAdapter(
        jobId              = jobId,
        rabbitMqService    = rabbitMqService,
        jobRequestHandler  = jobRequestHandler,
        storageFactory     = storageFactory,
        taskScheduler      = mock[TaskScheduler],
        storageCleaner     = mock[StorageCleaner],
        resourcesBasePaths = Seq(),
        heartbeatInterval  = any[Int]
      ) {
        override protected def startHeartbeat(jobId: UUID, heartbeat: Int): Unit = ()
      }
      jobService.start()

      // check success job in the queue
      val isFailureHandled = Promise[Boolean]()

      rabbitMqService.subscribe[JobMessage](JobMasterOutQueue, handle = {
        case JobMessage(_, JobResultFailure(_, _, _, _)) => isFailureHandled.success(true)
        case _ => isFailureHandled.failure(new Exception("unexpected message"))
      })

      whenReady(isFailureHandled.future) { result =>
        rabbitMqService.stop()
        actorSystem.terminate()
        Await.ready(actorSystem.whenTerminated, Duration(1, TimeUnit.MINUTES))
        result shouldBe true
      }
    }

    "on job failure if it's UserCortexException do nothing" in {
      val jobParamsPath = "hdfs://some/input/path"
      val jobMessage = JobMessage(
        JobMessageMeta(jobUuid, Some("TRAIN")),
        SubmitJob(jobParamsPath)
      )
      val jobRequest = JobRequest(CVModelTrain, ByteString.EMPTY)

      val rabbitMqService = mock[RabbitMqService]
      val storageFactory = mock[BaseStorageFactory]
      val reader = stub[StorageReader[JobRequest]]
      val jobRequestHandler = stub[JobRequestHandler]
      val resultFuture: JobRequestPartialHandler.JobResult = Future.failed(
        UserCortexException("errorCode", "errorMessage", "stackTrace")
      )

      (jobRequestHandler.handleJobRequest _).when((jobId, jobRequest)).returns(resultFuture)
      (reader.get _).when(jobParamsPath).returns(jobRequest)

      (storageFactory.createParamResultStorageReader[JobRequest]()(_: GeneratedMessageCompanion[JobRequest]))
        .expects(*).returning(reader)

      val jobService = new OrionJobServiceAdapter(
        jobId              = jobId,
        rabbitMqService    = rabbitMqService,
        jobRequestHandler  = jobRequestHandler,
        storageFactory     = storageFactory,
        taskScheduler      = mock[TaskScheduler],
        storageCleaner     = mock[StorageCleaner],
        resourcesBasePaths = Seq(),
        heartbeatInterval  = any[Int]
      ) {
        override def start(): Unit = {
          handle(jobMessage)
        }

        override protected def startHeartbeat(jobId: UUID, heartbeat: Int): Unit = ()
      }
      jobService.start()

      whenReady(resultFuture.failed) { exception =>
        (rabbitMqService.sendMessageToExchange _).expects(*, *, *).never()
        exception shouldBe an[UserCortexException]
      }
    }

    "on job failure if it's SystemCortexException do nothing" in {
      val jobParamsPath = "hdfs://some/input/path"
      val jobMessage = JobMessage(
        JobMessageMeta(jobUuid, Some("TRAIN")),
        SubmitJob(jobParamsPath)
      )
      val jobRequest = JobRequest(CVModelTrain, ByteString.EMPTY)

      val rabbitMqService = mock[RabbitMqService]
      val storageFactory = mock[BaseStorageFactory]
      val reader = stub[StorageReader[JobRequest]]
      val jobRequestHandler = stub[JobRequestHandler]
      val resultFuture: JobRequestPartialHandler.JobResult = Future.failed(
        SystemCortexException("errorMessage", "stackTrace")
      )

      (jobRequestHandler.handleJobRequest _).when((jobId, jobRequest)).returns(resultFuture)
      (reader.get _).when(jobParamsPath).returns(jobRequest)

      (storageFactory.createParamResultStorageReader[JobRequest]()(_: GeneratedMessageCompanion[JobRequest]))
        .expects(*).returning(reader)

      val jobService = new OrionJobServiceAdapter(
        jobId              = jobId,
        rabbitMqService    = rabbitMqService,
        jobRequestHandler  = jobRequestHandler,
        storageFactory     = storageFactory,
        taskScheduler      = mock[TaskScheduler],
        storageCleaner     = mock[StorageCleaner],
        resourcesBasePaths = Seq(),
        heartbeatInterval  = any[Int]
      ) {
        override def start(): Unit = {
          handle(jobMessage)
        }

        override protected def startHeartbeat(jobId: UUID, heartbeat: Int): Unit = ()
      }
      jobService.start()

      whenReady(resultFuture.failed) { exception =>
        (rabbitMqService.sendMessageToExchange _).expects(*, *, *).never()
        exception shouldBe an[SystemCortexException]
      }
    }

    "on job failure if it's SchedulerStoppedCortexException do nothing" in {
      val jobParamsPath = "hdfs://some/input/path"
      val jobMessage = JobMessage(
        JobMessageMeta(jobUuid, Some("TRAIN")),
        SubmitJob(jobParamsPath)
      )
      val jobRequest = JobRequest(CVModelTrain, ByteString.EMPTY)

      val rabbitMqService = mock[RabbitMqService]
      val storageFactory = mock[BaseStorageFactory]
      val reader = stub[StorageReader[JobRequest]]
      val jobRequestHandler = stub[JobRequestHandler]
      val resultFuture: JobRequestPartialHandler.JobResult = Future.failed(SchedulerStoppedCortexException())

      (jobRequestHandler.handleJobRequest _).when((jobId, jobRequest)).returns(resultFuture)
      (reader.get _).when(jobParamsPath).returns(jobRequest)

      (storageFactory.createParamResultStorageReader[JobRequest]()(_: GeneratedMessageCompanion[JobRequest]))
        .expects(*).returning(reader)

      val jobService = new OrionJobServiceAdapter(
        jobId              = jobId,
        rabbitMqService    = rabbitMqService,
        jobRequestHandler  = jobRequestHandler,
        storageFactory     = storageFactory,
        taskScheduler      = mock[TaskScheduler],
        storageCleaner     = mock[StorageCleaner],
        resourcesBasePaths = Seq(),
        heartbeatInterval  = any[Int]
      ) {
        override def start(): Unit = {
          handle(jobMessage)
        }

        override protected def startHeartbeat(jobId: UUID, heartbeat: Int): Unit = ()
      }
      jobService.start()

      whenReady(resultFuture.failed) { exeption =>
        (rabbitMqService.sendMessageToExchange _).expects(*, *, *).never()
        exeption shouldBe an[SchedulerStoppedCortexException]
      }
    }

    "handle job canceling when receives CancelJob request" in {
      val jobMessage = JobMessage(
        JobMessageMeta(jobUuid, Some("TRAIN")),
        CancelJob
      )
      val resourceBasePath = "resource/base/path"

      implicit val actorSystem: ActorSystem = ActorSystem("JobServiceSpec")
      val rabbitMqService = new RabbitMqService(actorSystem)
      rabbitMqService.sendMessageToExchange(jobMessage, DataDistributorExchange, jobMasterInRoutingKey)

      val taskScheduler = stub[TaskScheduler]
      val storageCleaner = stub[StorageCleaner]

      val jobService = new OrionJobServiceAdapter(
        jobId              = jobId,
        rabbitMqService    = rabbitMqService,
        jobRequestHandler  = mock[JobRequestHandler],
        storageFactory     = mock[BaseStorageFactory],
        taskScheduler      = taskScheduler,
        storageCleaner     = storageCleaner,
        resourcesBasePaths = Seq(resourceBasePath),
        heartbeatInterval  = any[Int]
      ) {
        def isHeartbeating(): Boolean = {
          sendingHeartbeats.get()
        }
      }

      jobService.start()

      // check success job in the queue
      val isTerminationHandled = Promise[Boolean]()

      rabbitMqService.subscribe[JobMessage](JobMasterOutQueue, handle = {
        case JobMessage(JobMessageMeta(`jobUuid`, None), JobMasterAppReadyForTermination) => isTerminationHandled.success(true)
        case _ => isTerminationHandled.failure(new Exception("unexpected message"))
      })

      whenReady(isTerminationHandled.future) { result =>
        rabbitMqService.stop()
        actorSystem.terminate()
        Await.ready(actorSystem.whenTerminated, Duration(1, TimeUnit.MINUTES))

        (taskScheduler.stop _).verify()
        (storageCleaner.deleteRecursively _).verify(resourceBasePath + "/" + jobUuid)
        jobService.isHeartbeating() shouldBe false
        result shouldBe true
      }
    }

    "don't handle job canceling when receives CancelJob request second time" in {
      val jobMessage = JobMessage(
        JobMessageMeta(jobUuid, Some("TRAIN")),
        CancelJob
      )
      val resourceBasePath = "resource/base/path"

      val rabbitMqService = stub[RabbitMqService]
      val taskScheduler = stub[TaskScheduler]
      val storageCleaner = stub[StorageCleaner]

      val jobService = new OrionJobServiceAdapter(
        jobId              = jobId,
        rabbitMqService    = rabbitMqService,
        jobRequestHandler  = mock[JobRequestHandler],
        storageFactory     = mock[BaseStorageFactory],
        taskScheduler      = taskScheduler,
        storageCleaner     = storageCleaner,
        resourcesBasePaths = Seq(resourceBasePath),
        heartbeatInterval  = any[Int]
      ) {
        override def start(): Unit = {
          handle(jobMessage)
        }
      }

      jobService.start()
      jobService.start()

      (taskScheduler.stop _).verify()
      (storageCleaner.deleteRecursively _).verify(resourceBasePath + "/" + jobUuid)

      val terminationJobMessage = JobMessage(JobMessageMeta(jobUuid), JobMasterAppReadyForTermination)
      (rabbitMqService.sendMessageToExchange _).verify(terminationJobMessage, GatewayExchange, jobMasterOutRoutingKey)
    }

    "send JobResultFailure to DataDistributorExchange if handler throws exception" in {
      val jobParamsPath = "hdfs://some/input/path"
      val jobMessage = JobMessage(
        JobMessageMeta(jobUuid, Some("TRAIN")),
        SubmitJob(jobParamsPath)
      )
      val jobRequest = JobRequest(CVModelTrain, ByteString.EMPTY)

      implicit val actorSystem: ActorSystem = ActorSystem("JobServiceSpec")
      val rabbitMqService = new RabbitMqService(actorSystem)
      rabbitMqService.sendMessageToExchange(jobMessage, DataDistributorExchange, jobMasterInRoutingKey)

      val storageFactory = mock[BaseStorageFactory]
      val reader = stub[StorageReader[JobRequest]]
      val jobRequestHandler = stub[JobRequestHandler]

      (reader.get _).when(jobParamsPath).returns(jobRequest)
      (storageFactory.createParamResultStorageReader[JobRequest]()(_: GeneratedMessageCompanion[JobRequest]))
        .expects(*).returning(reader)
      (jobRequestHandler.handleJobRequest _).when((jobId, jobRequest)).throws(new Exception(""))

      val jobService = new OrionJobServiceAdapter(
        jobId              = jobId,
        rabbitMqService    = rabbitMqService,
        jobRequestHandler  = jobRequestHandler,
        storageFactory     = storageFactory,
        taskScheduler      = mock[TaskScheduler],
        storageCleaner     = mock[StorageCleaner],
        resourcesBasePaths = Seq(),
        heartbeatInterval  = any[Int]
      ) {
        override protected def startHeartbeat(jobId: UUID, heartbeat: Int): Unit = ()
      }
      jobService.start()

      // check success job in the queue
      val isFailureHandled = Promise[Boolean]()

      rabbitMqService.subscribe[JobMessage](JobMasterOutQueue, handle = {
        case JobMessage(_, JobResultFailure(_, _, _, _)) => isFailureHandled.success(true)
        case _ => isFailureHandled.failure(new Exception("unexpected message"))
      })

      whenReady(isFailureHandled.future) { result =>
        rabbitMqService.stop()
        actorSystem.terminate()
        Await.ready(actorSystem.whenTerminated, Duration(1, TimeUnit.MINUTES))
        result shouldBe true
      }
    }
  }
}
