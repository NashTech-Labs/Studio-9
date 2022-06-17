package taurus.job

import java.time.{ ZoneOffset, ZonedDateTime }
import java.util.UUID

import akka.actor.{ ActorRef, Props }
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import cortex.api.argo.ConfigSetting
import cortex.api.pegasus.CreatedBy
import taurus.common.service.{ HttpClientSupport, Service, TimerSupport }
import taurus.job.BatchJobService.BatchJobActorMessages._
import taurus.job.BatchJobService.{ OnlinePredictionBatchJob, OnlinePredictionBatchJobSettings }
import taurus.sqs.SQSMessagesProvider
import taurus.sqs.SQSMessagesProvider.{ S3Record, _ }
import play.api.libs.json._
import cortex.api.argo.ConfigSetting.format
import cortex.api.taurus.StreamSettings
import taurus.common.utils.DurationExtensions._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

class BatchJobService(
    batchJobMessageHandler: ActorRef,
    sqsMessagesProvider:    SQSMessagesProvider,
    streamId:               String,
    batchSize:              Int,
    queueCheckInterval:     FiniteDuration,
    forceFetchTimeout:      FiniteDuration
) extends Service with TimerSupport with HttpClientSupport {

  implicit val ec = context.dispatcher
  val argoSettings = ArgoSettings(context.system)
  val argoResponseTimeout = argoSettings.argoResponseTimeout

  def fetchOnlineJobConfiguration(): Future[OnlinePredictionBatchJobSettings] = {
    def parseRawArgoSettings(rawSettings: String): OnlinePredictionBatchJobSettings = {
      (for {
        configSettings <- Json.fromJson[ConfigSetting](Json.parse(rawSettings))
        settings <- Json.fromJson[StreamSettings](Json.parse(configSettings.settingValue))
      } yield {
        OnlinePredictionBatchJobSettings(
          streamId        = settings.id,
          owner           = settings.owner,
          modelId         = settings.modelId,
          albumId         = settings.albumId,
          bucketName      = settings.s3Settings.bucketName,
          awsRegion       = settings.s3Settings.awsRegion,
          awsAccessKey    = settings.s3Settings.awsAccessKey,
          awsSecretKey    = settings.s3Settings.awsSecretKey,
          awsSessionToken = settings.s3Settings.awsSessionToken,
          targetPrefix    = settings.targetPrefix
        )
      }).recoverTotal {
        case JsError(errors) =>
          val errMsg = s"unexpected response from argo, message - $rawSettings"
          log.error(s"$errMsg, errors for json paths ${errors.map(_._1.toString())}")
          throw new Exception(errMsg)
      }
    }

    val credentials = Some(BasicHttpCredentials(argoSettings.username, argoSettings.password))
    this.get(argoSettings.streamSettingsUrl(streamId), credentials)
      .flatMap(_.entity.toStrict(argoResponseTimeout).map(_.data.utf8String))
      .map(parseRawArgoSettings)
  }

  def createOnlinePredictionJob(): Unit = {
    val messages = sqsMessagesProvider.receiveMessages()
    val onlinePredictionRecords = messages.map(_.toS3Record)
    val createJobF = fetchOnlineJobConfiguration().map(onlinePredictionJobSettings => {
      OnlinePredictionBatchJob(
        onlinePredictionRecords,
        onlinePredictionJobSettings
      )
    })

    createJobF onComplete {
      case Success(job) =>
        if (job.records.nonEmpty) {
          sqsMessagesProvider.deleteMessages(messages)
          batchJobMessageHandler ! job
        }

      case Failure(e) =>
        log.error(s"failed to create online prediction job, reason - ${e.getMessage}")
    }
  }

  override def preStart(): Unit = {
    self ! CheckSqs
    startTimer()
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.error(s"batch job service died with an exception - ${reason.getMessage}, message - ${message.getOrElse("<empty>")}")
    super.preRestart(reason, message)
  }

  override def receive: Receive = super.receive orElse {
    case CheckSqs =>
      context.system.scheduler.scheduleOnce(queueCheckInterval) {
        self ! CheckSqs
      }
      if (sqsMessagesProvider.getApproximateNumberOfMessages() >= batchSize) {
        createOnlinePredictionJob()
        restartTimer()
      }

    case ForceFetchSqs =>
      createOnlinePredictionJob()
      restartTimer()
  }

  override val timerValue: FiniteDuration = forceFetchTimeout

  override def onTimerTrigger(): Unit = {
    self ! ForceFetchSqs
  }
}

object BatchJobService {
  object BatchJobActorMessages {
    case object CheckSqs
    case object ForceFetchSqs
  }

  case class OnlinePredictionBatchJobSettings(
    streamId:        String,
    owner:           String,
    modelId:         String,
    albumId:         String,
    bucketName:      String,
    awsRegion:       String,
    awsAccessKey:    String,
    awsSecretKey:    String,
    awsSessionToken: Option[String],
    targetPrefix:    String,
    createdAt:       ZonedDateTime  = ZonedDateTime.now(ZoneOffset.UTC),
    createdBy:       CreatedBy      = CreatedBy.Taurus
  )

  case class OnlinePredictionBatchJob(
    records:  Seq[S3Record],
    settings: OnlinePredictionBatchJobSettings,
    jobId:    UUID                             = UUID.randomUUID()
  )

  //scalastyle:off
  def props(
    batchJobMessageHandler: ActorRef,
    sqsMessagesProvider:    SQSMessagesProvider,
    streamId:               String,
    batchSize:              Int                 = 100,
    queueCheckInterval:     FiniteDuration      = 10.seconds,
    forceFetchTimeout:      FiniteDuration      = 1.minute
  ): Props = {
    Props(new BatchJobService(
      batchJobMessageHandler = batchJobMessageHandler,
      sqsMessagesProvider    = sqsMessagesProvider,
      streamId               = streamId,
      batchSize              = batchSize,
      queueCheckInterval     = queueCheckInterval,
      forceFetchTimeout      = forceFetchTimeout
    ))
  }
}
