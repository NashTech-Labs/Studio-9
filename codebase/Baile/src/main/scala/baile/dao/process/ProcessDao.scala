package baile.dao.process

import java.time.Instant
import java.util.UUID

import baile.dao.CommonSerializers
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.dao.process.ProcessDao._
import baile.daocommons.WithId
import baile.daocommons.filters.{ Filter, IdIs }
import baile.daocommons.sorting.Field
import baile.domain.asset.AssetType
import baile.domain.process.{ Process, ProcessStatus, ResultHandlerMeta }
import baile.utils.TryExtensions._
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Updates.push
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.{ BsonArray, BsonDocument, BsonDouble, BsonString }
import org.mongodb.scala.{ Document, MongoDatabase }
import play.api.libs.json.{ JsObject, Json }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.Try

object ProcessDao {

  case class TargetIdIs(id: String) extends Filter
  case class TargetTypeIs(targetType: AssetType) extends Filter
  case class StatusIs(status: ProcessStatus) extends Filter
  case class AuthTokenIs(token: String) extends Filter
  case class HandlerClassNameIs(className: String) extends Filter
  case class HandlerClassNameIn(classNames: Seq[String]) extends Filter
  case class OwnerIdIs(userId: UUID) extends Filter
  case class ProcessStartedBetween(timeFrom: Instant, timeTo: Instant) extends Filter
  case class ProcessCompletedBetween(timeFrom: Instant, timeTo: Instant) extends Filter

  case object Created extends Field
  case object Started extends Field
  case object Completed extends Field

}

class ProcessDao(protected val database: MongoDatabase) extends MongoEntityDao[Process] {

  override val collectionName: String = "processes"

  def addHandler(
    id: String,
    resultHandlerMeta: ResultHandlerMeta
  )(implicit ec: ExecutionContext): Future[Option[WithId[Process]]] =
    for {
      predicate <- buildPredicate(IdIs(id)).toFuture
      updateResult <- collection.updateOne(
        predicate,
        push("auxiliaryOnComplete", resultHandlerMetaToDocument(resultHandlerMeta))
      ).toFuture
      result <- {
        if (updateResult.getModifiedCount > 0) get(id)
        else Future.successful(None)
      }
    } yield result

  override protected[process] def entityToDocument(process: Process): Document = Document(
    "targetId" -> process.targetId,
    "targetType" -> CommonSerializers.assetTypeToString(process.targetType),
    "ownerId" -> process.ownerId.toString,
    "authToken" -> process.authToken.map(BsonString(_)),
    "jobId" -> process.jobId.toString,
    "status" -> processStatusMap(process.status),
    "progress" -> process.progress.map(BsonDouble(_)),
    "estimatedTimeRemaining" -> process.estimatedTimeRemaining.map(_.toString),
    "created" -> process.created.toString,
    "started" -> process.started.map(_.toString),
    "completed" -> process.completed.map(_.toString),
    "errorCauseMessage" -> process.errorCauseMessage.map(_.toString),
    "errorDetails" -> process.errorDetails.map(_.toString),
    "onComplete" -> resultHandlerMetaToDocument(process.onComplete),
    "auxiliaryOnComplete" -> process.auxiliaryOnComplete.map(resultHandlerMetaToDocument)
  )

  override protected[process] def documentToEntity(document: Document): Try[Process] = Try {
    Process(
      targetId = document.getMandatory[BsonString]("targetId").getValue,
      targetType = CommonSerializers.assetTypeFromString(document.getMandatory[BsonString]("targetType").getValue),
      ownerId = UUID.fromString(document.getMandatory[BsonString]("ownerId").getValue),
      authToken = document.get[BsonString]("authToken").map(_.getValue),
      jobId = UUID.fromString(document.getMandatory[BsonString]("jobId").getValue),
      status = processStatusMap.map(_.swap).apply(document.getMandatory[BsonString]("status").getValue),
      progress = document.get[BsonDouble]("progress").map(_.getValue),
      estimatedTimeRemaining = {
        document.get[BsonString]("estimatedTimeRemaining").map { raw =>
          val duration = Duration(raw.getValue)
          FiniteDuration(duration.length, duration.unit.toString)
        }
      },
      created = Instant.parse(document.getMandatory[BsonString]("created").getValue),
      started = document.get[BsonString]("started").map(started => Instant.parse(started.getValue)),
      completed = document.get[BsonString]("completed").map(completed => Instant.parse(completed.getValue)),
      errorCauseMessage = document.get[BsonString]("errorCauseMessage").map(_.getValue),
      errorDetails = document.get[BsonString]("errorDetails").map(_.getValue),
      onComplete = documentToResultHandlerMeta(document.getChildMandatory("onComplete")),
      auxiliaryOnComplete = document.getMandatory[BsonArray]("auxiliaryOnComplete").map { value =>
        documentToResultHandlerMeta(value.asDocument)
      }
    )
  }

  private def resultHandlerMetaToDocument(resultHandlerMeta: ResultHandlerMeta): Document =
    Document(
      "handlerClassName" -> BsonString(resultHandlerMeta.handlerClassName),
      "meta" -> BsonDocument(resultHandlerMeta.meta.toString)
    )

  private def documentToResultHandlerMeta(document: Document): ResultHandlerMeta =
    ResultHandlerMeta(
      handlerClassName = document.getMandatory[BsonString]("handlerClassName").getValue,
      meta = Json.parse(document.getChildMandatory("meta").toJson).as[JsObject]
    )

  private val processStatusMap: Map[ProcessStatus, String] = Map(
    ProcessStatus.Queued -> "QUEUED",
    ProcessStatus.Running -> "RUNNING",
    ProcessStatus.Completed -> "COMPLETED",
    ProcessStatus.Cancelled -> "CANCELLED",
    ProcessStatus.Failed -> "FAILED"
  )

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Bson]] = {
    case TargetIdIs(targetId) =>
      Try(Filters.equal("targetId", targetId))
    case AuthTokenIs(token) =>
      Try(Filters.equal("authToken", token))
    case TargetTypeIs(targetType) =>
      Try(Filters.equal("targetType", CommonSerializers.assetTypeToString(targetType)))
    case StatusIs(status) =>
      Try(Filters.equal("status", processStatusMap(status)))
    case HandlerClassNameIs(className) =>
      Try(Filters.equal("onComplete.handlerClassName", className))
    case OwnerIdIs(ownerId) => Try(Filters.equal("ownerId", ownerId.toString))
    case HandlerClassNameIn(classNames) => Try(Filters.in("onComplete.handlerClassName", classNames: _*))
    case ProcessStartedBetween(dateTimeFrom, dateTimeTo) =>
      Try {
        Filters.and(
          Filters.gte("started", dateTimeFrom.toString),
          Filters.lte("started", dateTimeTo.toString)
        )
      }
    case ProcessCompletedBetween(dateTimeFrom, dateTimeTo) =>
      Try {
        Filters.and(
          Filters.gte("completed", dateTimeFrom.toString),
          Filters.lte("completed", dateTimeTo.toString)
        )
      }
  }

  override protected val fieldMapper: Map[Field, String] = Map(
    Created -> "created",
    Started -> "started",
    Completed -> "completed"
  )

}
