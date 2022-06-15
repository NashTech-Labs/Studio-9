package cortex.jobmaster.job

import java.util.UUID

import akka.actor.ActorSystem
import cortex.common.logging.JMLoggerFactory
import cortex.jobmaster.jobs.job.TestUtils
import cortex.jobmaster.jobs.job.cross_validation.CrossValidationJob.CrossValidationParams
import cortex.jobmaster.jobs.job.cross_validation.{ CrossValidationJob, CrossValidationJobConfig }
import cortex.jobmaster.jobs.job.splitter.{ SplitterJob, SplitterJobConfig }
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.orion.service.domain.JobRequestHandler
import cortex.jobmaster.orion.service.io.{ BaseStorageFactory, S3ParamResultStorageFactory, S3StorageCleaner }
import cortex.jobmaster.orion.service.{ OrionJobServiceAdapter, RabbitMqService }
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.tabular_data.mvh.MVHModule
import cortex.task.tabular_data.mvh.MVHParams.MVHTrainParams
import cortex.task.tabular_data.tabularpipeline.TabularPipelineModule
import cortex.task.tabular_data.{ AllowedModelPrimitive, AllowedTaskType }
import cortex.task.transform.splitter.SplitterModule
import cortex.task.transform.splitter.SplitterParams.SplitterTaskParams
import cortex.testkit.{ FutureTestUtils, WithLogging, WithS3AndLocalScheduler }
import org.mockito.Matchers.any
import org.scalamock.scalatest.MockFactory
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import org.scalatest.concurrent.Eventually

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.reflectiveCalls
import scala.util.Try


//TODO sometimes it fails while running from sbt
class E2ECrossValidationCancellingTest extends FlatSpec
  with FutureTestUtils
  with WithS3AndLocalScheduler
  with Eventually
  with MockFactory
  with WithLogging {

  import E2ECrossValidationCancellingTest._

  "CrossValidationJob" should "be stopped and all resources be released" in {
    val inputDsPath = s"input/train.csv"
    val jobsPath = "some/path"
    val modelsBasePath = "tabular/models"
    TestUtils.copyToS3(fakeS3Client, baseBucket, inputDsPath, "../test_data/train_pipeline.csv")

    val s3AccessParams = S3AccessParams(
      bucket = baseBucket,
      accessKey = accessKey,
      secretKey = secretKey,
      region = region,
      endpointUrl = Some(fakeS3Endpoint)
    )
    val splitter = new SplitterModule(5, "output-", jobsPath)
    val mvhModule = new MVHModule(modelsBasePath)
    val splitterJob = new SplitterJob(taskScheduler, splitter, SplitterJobConfig(1.0, 512.0))
    val tabularPipelineModule = new TabularPipelineModule(jobsPath)
    val cvJob = new CrossValidationJob(taskScheduler, tabularPipelineModule, s3AccessParams, CrossValidationJobConfig(1.0, 512.0))
    val jobId = UUID.randomUUID()
    val system = any[ActorSystem]
    val rabbitMqService = new RabbitMqService(system)
    val hdfsStorageCleaner = new S3StorageCleaner(fakeS3Client, baseBucket)
    val jobServiceAdapter = new OrionJobServiceAdapterWithCancel(
      jobId = jobId.toString,
      rabbitMqService = rabbitMqService,
      storageFactory = mock[S3ParamResultStorageFactory],
      taskScheduler = taskScheduler,
      resourcesBasePaths = Seq(jobsPath),
      s3StorageCleaner = hdfsStorageCleaner
    )

    val mvhTrainParams = MVHTrainParams(
      trainInputPaths       = Seq(inputDsPath),
      numericalPredictors   = Seq("second", "fourth"),
      categoricalPredictors = Seq("first", "third"),
      storageAccessParams   = s3AccessParams,
      modelsBasePath        = modelsBasePath
    )
    val mvhTrainTaskId = UUID.randomUUID().toString
    val mvhTrainTask = mvhModule.trainTask(
      id       = mvhTrainTaskId,
      jobId    = mvhTrainTaskId,
      taskPath = mvhTrainTaskId,
      params   = mvhTrainParams,
      cpus     = 1.0,
      memory   = 512
    )

    val cvScore: Future[(Double, JobTimeInfo.TasksTimeInfo)] = for {
      mvhTrainResult <- taskScheduler.submitTask(mvhTrainTask)
      splitterResult <- {
        val params = SplitterTaskParams(Seq(inputDsPath), s3AccessParams)
        splitterJob.splitInput(jobId.toString, params)
      }
      cvScore <- {
        val cvParams = CrossValidationParams(
          taskType              = AllowedTaskType.Regressor,
          modelPrimitive        = AllowedModelPrimitive.Linear,
          response              = "ycol",
          numericalPredictors   = Seq("second", "fourth"),
          categoricalPredictors = Seq("first", "third"),
          weightsCol            = None,
          hyperparamsDict       = Map(),
          mvhModelId            = mvhTrainResult.modelReference.id,
          modelsBasePath        = modelsBasePath
        )
        cvJob.getCVScore(jobId.toString, splitterResult, cvParams)
      }
    } yield cvScore

    //TODO kinda hack for emulating jobs initial bootstrap
    Thread.sleep(3 * 1000)
    fakeS3Client.getFiles(baseBucket, Some(jobsPath)).size should be > 0

    jobServiceAdapter.cancel(jobId)
    //ignoring exception here
    Try(cvScore.await())

    //validate that resources was released
    fakeS3Client.getFiles(baseBucket, Some(jobsPath)).size shouldBe 0

    //validate that only "expected" resources was released
    fakeS3Client.getFiles(baseBucket, Some(inputDsPath)).size shouldBe 1
    taskScheduler.stopInvocations() shouldBe 1
  }
}

object E2ECrossValidationCancellingTest {
  class OrionJobServiceAdapterWithCancel(
    jobId: String,
    rabbitMqService: RabbitMqService,
    storageFactory: BaseStorageFactory,
    taskScheduler: TaskScheduler,
    resourcesBasePaths: Seq[String],
    s3StorageCleaner: S3StorageCleaner
  )(implicit ec: ExecutionContext, loggerFactory: JMLoggerFactory) extends OrionJobServiceAdapter(
    jobId = jobId,
    rabbitMqService = rabbitMqService,
    jobRequestHandler = any[JobRequestHandler],
    storageFactory = storageFactory,
    taskScheduler = taskScheduler,
    storageCleaner = s3StorageCleaner,
    resourcesBasePaths = resourcesBasePaths,
    heartbeatInterval = any[Int]
  )(ec, loggerFactory) {
    def cancel(jobId: UUID): Unit = {
      super.cancelJob(jobId)
    }

    override def sendReadyForTerminationSignal(jobId: UUID): Unit = ()
  }
}
