package baile.services.common

import java.text.SimpleDateFormat
import java.util.{ Date, UUID }

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.scaladsl.{ Broadcast, GraphDSL, Keep, RunnableGraph, Sink, Source }
import akka.stream.{ ClosedShape, KillSwitches, Materializer, StreamLimitReachedException }
import akka.util.ByteString
import baile.domain.usermanagement.User
import baile.services.common.MLEntityExportImportService.{
  EntityFileSavedResult, EntityImportError, ExportedMetaFormat
}
import baile.services.common.MLEntityExportImportService.EntityImportError.{
  ImportHandlingFailed, InvalidMetaFormat, MetaIsTooBig
}
import baile.services.cortex.job.CortexJobService
import baile.services.remotestorage.RemoteStorageService
import baile.utils.ThrowableExtensions._
import cats.data.EitherT
import cats.implicits._
import com.fasterxml.jackson.core.JsonProcessingException
import play.api.libs.json._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class MLEntityExportImportService(
  cortexJobService: CortexJobService,
  entitiesFileStorage: RemoteStorageService,
  maxEntityMetaSize: Long
)(
  implicit ec: ExecutionContext,
  logger: LoggingAdapter
) {

  private val entityMetaDelimiterCharacter: Char = '\u0000'

  private[services] def importEntity[E, R, M: Reads](
    source: Source[ByteString, Any],
    metaValidator: M => EitherT[Future, E, Unit],
    handler: EntityFileSavedResult[M] => Future[Either[E, R]]
  )(implicit user: User, materializer: Materializer): Future[Either[EntityImportError[E], R]] = {

    val filePath: String = {
      val date = new Date
      val dateStamp = new SimpleDateFormat("yyyyMMdd").format(date)
      val randomSuffix = UUID.randomUUID().toString
      entitiesFileStorage.path(
        "baile",
        "source",
        "uploaded-ml-entities",
        user.id.toString,
        dateStamp.toString,
        s"ml-entity-$randomSuffix"
      )
    }

    val killableSource = source.viaMat(KillSwitches.single)(Keep.right)
    val metaSink = Sink.fold[Array[Byte], Byte](Array.empty[Byte])(_ :+ _)
    val fileSink = entitiesFileStorage.getSink(filePath)

    val graph = RunnableGraph.fromGraph(
      GraphDSL.create(
        killableSource,
        metaSink,
        fileSink
      )((_, _, _)) { implicit builder => (sourceShape, metaSinkShape, fileSinkShape) =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[ByteString](2))

        sourceShape ~> broadcast.in

        val metaOut = broadcast.out(0)
          .mapConcat(identity)
          .takeWhile(_ != entityMetaDelimiterCharacter)
          .limit(maxEntityMetaSize)

        val fileOut = broadcast.out(1)
          .dropWhile { byteString =>
            !byteString.contains(entityMetaDelimiterCharacter)
          }.zipWithIndex.map {
            // Not to lose any content of byte string which happens to contain delimiter
            case (firstByteString, 0) => firstByteString.dropWhile(_ != entityMetaDelimiterCharacter).drop(1)
            case (byteString, _) => byteString
          }

        metaOut ~> metaSinkShape.in
        fileOut ~> fileSinkShape.in

        ClosedShape
      })

    val (killSwitch, metaF, fileUploadF) = graph.run()

    def abortUploadingWithError(e: EntityImportError[E]): Unit = killSwitch.abort(new RuntimeException(e.toString))

    val parsedMetaF = metaF.map[Either[EntityImportError[E], M]] { rawJson =>
      val json = Json.parse(rawJson)
      Json.fromJson[ExportedMetaFormat[M]](json) match {
        case JsSuccess(meta, _) =>
          meta.entityMeta.asRight
        case jsError: JsError =>
          val error = InvalidMetaFormat(JsError.toJson(jsError).toString)
          abortUploadingWithError(error)
          error.asLeft
      }
    }.recover { case ex =>
      val error = ex match {
        case _: StreamLimitReachedException => MetaIsTooBig
        case ex: JsonProcessingException => InvalidMetaFormat(s"Error parsing meta as json: ${ ex.getMessage }")
      }
      abortUploadingWithError(error)
      error.asLeft
    }

    val result = for {
      meta <- EitherT(parsedMetaF)
      _ <- metaValidator(meta).leftMap { handlingError =>
        val error = ImportHandlingFailed(handlingError)
        abortUploadingWithError(error)
        error
      }
      _ <- EitherT.right[EntityImportError[E]](fileUploadF)
      result <- EitherT(handler(EntityFileSavedResult(meta, filePath)))
        .leftMap[EntityImportError[E]](ImportHandlingFailed(_))
    } yield result

    val resultValue = result.value
    resultValue.onComplete {
      case Success(Left(_)) => deleteImportedEntityFile(filePath)
      case Failure(_) => deleteImportedEntityFile(filePath)
      case Success(Right(_)) => ()
    }
    resultValue
  }

  private[services] def exportEntity[M: Writes](
    entityFilePath: String,
    meta: M
  ): Future[Source[ByteString, NotUsed]] =
    entitiesFileStorage.streamFile(entityFilePath).map { entityFile =>
      val rawJson = Json.stringify(Json.toJson(
        ExportedMetaFormat(
          metaVersion = "1.0",
          meta
        )
      ))
      val jsonBytes = ByteString(rawJson.getBytes)

      entityFile.content.prepend[ByteString, NotUsed] {
        Source.single(jsonBytes :+ entityMetaDelimiterCharacter.toByte)
      }
    }

  private[services] def deleteImportedEntityFile(filePath: String): Future[Unit] =
    entitiesFileStorage.delete(filePath).recover {
      case ex =>
        logger.warning(
          "Failed to delete imported entity file [{}]. Probably file was already deleted at this point. Error: [{}]",
          filePath,
          ex.printInfo
        )
    }

}

private[services] object MLEntityExportImportService {

  case class ExportedMetaFormat[M](metaVersion: String, entityMeta: M)
  object ExportedMetaFormat {

    implicit def ExportedMetaReads[M: Reads]: Reads[ExportedMetaFormat[M]] =
      Json.reads[ExportedMetaFormat[M]]

    implicit def ExportedMetaWrites[M: Writes]: Writes[ExportedMetaFormat[M]] =
      Json.writes[ExportedMetaFormat[M]]

  }

  sealed trait EntityImportError[+E]
  object EntityImportError {
    case object MetaIsTooBig extends EntityImportError[Nothing]
    case class InvalidMetaFormat(error: String) extends EntityImportError[Nothing]
    case class ImportHandlingFailed[E](e: E) extends EntityImportError[E]
  }

  case class EntityFileSavedResult[M](meta: M, filePath: String)

}
