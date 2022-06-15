package pegasus.common.redshift

import akka.actor.ActorSystem
import com.amazonaws.regions.Regions
import cortex.api.pegasus.PredictionImportRequest
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

object RedshiftRepository {
  //TODO adjust limits better
  private val predictionTempTableColMappings = Seq(
    "job_id" -> "varchar(50) not null",
    "model_id" -> "varchar(50) not null",
    "album_path" -> "varchar(255) not null",
    "file_name" -> "varchar(255) not null",
    "file_path" -> "varchar(255) not null",
    "file_size" -> "integer not null",
    "label" -> "varchar(255) not null",
    "confidence" -> "decimal not null"
  )

  def apply(system: ActorSystem): RedshiftRepository = new RedshiftRepository {
    override val db = RedshiftDatabase(system).database

    // Note: use default dispatcher here. Slick manages its own dispatcher internally when doing the I/O operations.
    override implicit val ec: ExecutionContext = system.dispatcher
  }

  case class AwsCredentials(accessKeyId: String, secretAccessKey: String)
}

trait RedshiftRepository {
  import RedshiftRepository._

  val logger = LoggerFactory.getLogger("redshift-repository")
  val db: Database
  implicit val ec: ExecutionContext

  def uploadPrediction(
    predictionImportRequest: PredictionImportRequest,
    credentials:             AwsCredentials,
    region:                  Regions
  ): Future[Unit] = {
    //TODO provide filtered job id (pay attention to '-' char!!!)
    val tempTableName = s"cv_predictions_temp_${Random.alphanumeric.take(7).mkString}"
    val tempTypedColumns = predictionTempTableColMappings
      .map { case (colName, colType) => s"$colName $colType" }
      .mkString(",")
    val tempColumns = predictionTempTableColMappings
      .map(_._1)
      .mkString(",")
    val createTempTable = sqlu"""create temp table #${tempTableName} (#${tempTypedColumns}) diststyle even;"""

    val copy = sqlu""" copy #${tempTableName}(#${tempColumns}) from
            '#${predictionImportRequest.s3PredictionCsvPath}'
             access_key_id '#${credentials.accessKeyId}'
             secret_access_key '#${credentials.secretAccessKey}'
             delimiter ','
             region '#${region.getName()}'
             CSV
             ignoreheader 1"""

    val insert = sqlu"""
            insert into "predictions"."cv_predictions" (#${tempColumns},
            "stream_id", "owner", "album_id", "created_at", "created_by")
            select "job_id", "model_id", "album_path",
             "file_name", "file_path", "file_size", "label", "confidence",
             '#${predictionImportRequest.streamId}', '#${predictionImportRequest.owner}',
             '#${predictionImportRequest.albumId}', '#${predictionImportRequest.createdAt}',
             '#${predictionImportRequest.createdBy}' from #${tempTableName};
          """

    val drop = sqlu"drop table #${tempTableName}"

    val query = for {
      _ <- {
        logger.info(s"[JobId ${predictionImportRequest.jobId}] running create table - ${createTempTable.getDumpInfo.mainInfo}")
        createTempTable
      }
      _ <- {
        logger.info(s"[JobId ${predictionImportRequest.jobId}] running copy - ${copy.getDumpInfo.mainInfo}")
        copy
      }
      _ <- {
        logger.info(s"[JobId ${predictionImportRequest.jobId}] running insert - ${copy.getDumpInfo.mainInfo}")
        insert
      }
      _ <- {
        logger.info(s"[JobId ${predictionImportRequest.jobId}] running drop temp table - ${copy.getDumpInfo.mainInfo}")
        drop
      }
    } yield ()

    db.run(query.transactionally)
  }
}
