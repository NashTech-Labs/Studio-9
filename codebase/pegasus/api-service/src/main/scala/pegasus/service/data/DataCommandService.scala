package pegasus.service.data

import akka.actor.Props
import akka.pattern.pipe
import cortex.api.pegasus.{ PegasusJobStatus, PredictionImportRequest, PredictionImportResponse }
import pegasus.common.redshift.RedshiftRepository
import pegasus.common.service.{ NamedActor, Service }

object DataCommandService extends NamedActor {
  override val Name = "data-command-service"

  def props(): Props = Props(new DataCommandService())
}

class DataCommandService extends Service {
  import RedshiftRepository._

  implicit val ec = context.dispatcher

  val repository = RedshiftRepository(context.system)

  val s3Settings = S3Settings(context.system)
  val s3Region = s3Settings.region
  val s3Credentials = AwsCredentials(s3Settings.accessKeyId, s3Settings.secretAccessKey)

  def receive: Receive = {
    case req: PredictionImportRequest =>
      repository.uploadPrediction(req, s3Credentials, s3Region)
        .map(_ => PredictionImportResponse(req.jobId, PegasusJobStatus.Succeed))
        .recover {
          case ex =>
            log.error(s"[jobId: ${req.jobId}] [Uploading prediction results error] - Failed uploading prediction ${ex.getMessage}")
            PredictionImportResponse(req.jobId, PegasusJobStatus.Failed)
        } pipeTo sender
  }
}
