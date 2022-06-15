import java.time.{ ZoneOffset, ZonedDateTime }

import akka.actor.ActorSystem
import com.spingo.op_rabbit.RecoveryStrategy
import cortex.api.BaseJson4sFormats
import cortex.api.pegasus.{ CreatedBy, PredictionImportRequest, PredictionImportResponse }
import orion.ipc.rabbitmq.{ MlJobTopology, RabbitMqRpcClient }

import scala.concurrent.ExecutionContext.Implicits.global

// TODO for now, Pegasus doesn't have integration tests, when it will this object should be replaced with an appropriate test.
object PegasusClientExample {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val recoveryStrategy = RecoveryStrategy.limitedRedeliver()
    implicit val formats = BaseJson4sFormats.extend(
      PredictionImportRequest.CreatedByFormats,
      PredictionImportResponse.PegasusJobStatusFormats
    )

    val rmqClient = RabbitMqRpcClient(system)
    val predictionImportRequest = PredictionImportRequest(
      jobId               = "some_jobid",
      streamId            = "some_streamid",
      albumId             = "some_album",
      owner               = "some_owner",
      createdAt           = ZonedDateTime.now(ZoneOffset.UTC),
      createdBy           = CreatedBy.Taurus,
      s3PredictionCsvPath = "s3://dev.deepcortex.ai/cortex-job-master/e2e/online_sample"
    )

    rmqClient.sendMessageToExchange[PredictionImportRequest](
      predictionImportRequest,
      MlJobTopology.GatewayExchange,
      MlJobTopology.PegasusInRoutingKey.format("test-job")
    )

    //scalastyle:off
    rmqClient.subscribe[PredictionImportResponse](MlJobTopology.PegasusOutQueue)(println)
  }
}
