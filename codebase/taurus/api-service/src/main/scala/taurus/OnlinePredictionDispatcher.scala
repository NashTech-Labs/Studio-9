package taurus

import java.util.UUID

import akka.actor.{ ActorRef, PoisonPill, Props, Status }
import akka.pattern.ask
import akka.persistence.{ PersistentActor, RecoveryCompleted }
import akka.util.Timeout
import awscala.sqs.SQS
import com.amazonaws.regions.{ Region, Regions }
import cortex.api.baile.{ PredictionResultItem, SavePredictionResultRequest }
import cortex.api.job.online.prediction.PredictResponse
import cortex.api.pegasus.{ PegasusJobStatus, PredictionImportRequest, PredictionImportResponse }
import taurus.OnlinePredictionDispatcher.{ JobFinished, JobRegistered }
import taurus.baile.BaileService
import taurus.baile.BaileService.SavePredictionResult
import taurus.common.service.Service
import taurus.job.BatchJobService.{ OnlinePredictionBatchJob, OnlinePredictionBatchJobSettings }
import taurus.job.CortexJobSubmitter.{ StartJob, WatchJob }
import taurus.job.{ BatchJobService, CortexJobSubmitter, CortexSettings }
import taurus.sqs.SQSMessagesProvider

import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object OnlinePredictionDispatcher {

  def props(streamId: String, pegasusService: ActorRef): Props =
    Props(new OnlinePredictionDispatcher(streamId, pegasusService))

  private case class JobRegistered(batchJobId: UUID, batchJob: OnlinePredictionBatchJobSettings)

  private case class JobFinished(batchJobId: UUID, isSuccess: Boolean)

}

class OnlinePredictionDispatcher(streamId: String, pegasusService: ActorRef) extends Service with PersistentActor {

  log.debug("Starting Online Prediction Dispatcher for stream: [{}]", streamId)

  override val persistenceId: String = s"online-prediction-dispatcher-$streamId"

  private var jobs: Map[UUID, OnlinePredictionBatchJobSettings] = Map.empty

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  val cortexSettings = CortexSettings(context.system)
  val jobPollingMaxAttempts: Int = cortexSettings.jobPollingMaxAttempts
  val jobPollingInterval: FiniteDuration = cortexSettings.jobPollingInterval
  implicit val actorResponseTimeout: Timeout = 1.minute

  val batchJobService: ActorRef = {

    val sqsConfig = context.system.settings.config.getConfig("sqs-client")
    val sqsQueueName = sqsConfig.getString("queue-name")

    //TODO These config values should be taken from Argo.
    //TODO So BatchJobService should probably not load Argo settings by itself.
    val sqs = {
      val sqsAccessKey = sqsConfig.getString("access-key")
      val sqsSecretKey = sqsConfig.getString("secret-key")
      val sqsRegion = Region.getRegion(Regions.fromName(sqsConfig.getString("region")))

      SQS(sqsAccessKey, sqsSecretKey)(sqsRegion)
    }

    context.actorOf(BatchJobService.props(
      batchJobMessageHandler = self,
      streamId               = streamId,
      sqsMessagesProvider    = SQSMessagesProvider(sqsQueueName)(sqs)
    ))
  }

  override val receiveRecover: Receive = {
    case JobRegistered(batchJobId, batchJobSettings) =>
      registerJob(batchJobId, batchJobSettings)
    case JobFinished(batchJobId, _) =>
      finishJob(batchJobId)
    case RecoveryCompleted =>
      jobs.foreach {
        case (batchJobId, batchJobSettings) =>
          val cortexJobSubmitter = context.actorOf(CortexJobSubmitter.props(batchJobId))
          val result = waitJobResultAndProcessIt(batchJobId, batchJobSettings, cortexJobSubmitter)
          result andThen { case _ => cortexJobSubmitter ! PoisonPill }
      }
  }

  override def receiveCommand: Receive = {
    case batchJob: OnlinePredictionBatchJob =>
      val cortexJobSubmitter = context.actorOf(CortexJobSubmitter.props(batchJob.jobId))

      def startJob(): Future[Unit] = {
        (cortexJobSubmitter ? StartJob(batchJob)).map {
          case () =>
            persist(JobRegistered(batchJob.jobId, batchJob.settings))(_ =>
              registerJob(batchJob.jobId, batchJob.settings))
          case Status.Failure(e) =>
            throw e
        }
      }

      val result = for {
        _ <- startJob()
        _ <- waitJobResultAndProcessIt(batchJob.jobId, batchJob.settings, cortexJobSubmitter)
      } yield ()

      result andThen { case _ => cortexJobSubmitter ! PoisonPill }
  }

  private def registerJob(batchJobId: UUID, batchJobSettings: OnlinePredictionBatchJobSettings): Unit =
    jobs += batchJobId -> batchJobSettings

  private def finishJob(batchJobId: UUID): Unit =
    jobs -= batchJobId

  private def waitJobResultAndProcessIt(
    batchJobId:         UUID,
    batchJobSettings:   OnlinePredictionBatchJobSettings,
    cortexJobSubmitter: ActorRef
  ): Future[Unit] = {

    def loadJobResult(): Future[PredictResponse] = {
      val responseTimeout = jobPollingInterval * jobPollingMaxAttempts.toLong
      cortexJobSubmitter.ask(WatchJob)(responseTimeout).map {
        case predictResponse: PredictResponse =>
          predictResponse
        case Status.Failure(e) =>
          persist(JobFinished(batchJobId, isSuccess = false))(_ => finishJob(batchJobId))
          throw e
      }
    }

    def handleBailePart(predictResponse: PredictResponse): Future[Unit] = {
      val baileService = context.actorOf(BaileService.props())

      val savePredictionResultRequest = SavePredictionResultRequest(
        albumId = batchJobSettings.albumId,
        results = predictResponse.images.map { image =>
          PredictionResultItem(
            filePath   = image.filePath,
            fileSize   = image.fileSize,
            fileName   = image.fileName,
            metadata   = image.metadata,
            label      = image.label,
            confidence = image.confidence
          )
        }
      )

      val result = (baileService ? SavePredictionResult(batchJobId, savePredictionResultRequest)).map {
        case ()                => ()
        case Status.Failure(e) => throw e
      }

      result andThen {
        case _ => baileService ! PoisonPill
      }
    }

    def handlePegasusPart(predictResponse: PredictResponse): Future[Unit] = {
      val predictionImportRequest = PredictionImportRequest(
        jobId               = batchJobId.toString,
        streamId            = streamId,
        albumId             = batchJobSettings.albumId,
        owner               = batchJobSettings.owner,
        createdAt           = batchJobSettings.createdAt,
        createdBy           = batchJobSettings.createdBy,
        s3PredictionCsvPath = predictResponse.s3ResultsCsvPath
      )

      (pegasusService ? predictionImportRequest).map {
        case PredictionImportResponse(jobId, status) =>
          status match {
            //TODO this should be located in PegasusService
            case PegasusJobStatus.Succeed =>
              log.info("[ JobId: {} ] - [Save Job Result] - Pegasus part saving completed.", jobId)
            case PegasusJobStatus.Failed =>
              log.error("[ JobId: {} ] - [Save Job Result] - Pegasus part saving failed.", jobId)
              throw new Exception("Pegasus saving failed")
          }
        case unknown =>
          throw new Exception(s"Unexpected response from pegasus service $unknown")
      }
    }

    val result = loadJobResult().flatMap { predictResponse =>
      val bailePartF = handleBailePart(predictResponse)
      val pegasusPartF = handlePegasusPart(predictResponse)

      persist(JobFinished(batchJobId, isSuccess = true))(_ => finishJob(batchJobId))

      for {
        _ <- bailePartF
        _ <- pegasusPartF
      } yield ()
    }

    result andThen {
      case Success(_) => log.info(s"Batch Job [{}] finished.", batchJobId)
      case Failure(e) => log.error(s"Batch Job [{}] failed with error: [{}]", batchJobId, e)
    }

  }

}
