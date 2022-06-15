package baile.dao.mongo

import baile.daocommons.exceptions.UnknownSortingFieldException
import baile.daocommons.filters._
import baile.daocommons.sorting.Direction.{ Ascending, Descending }
import baile.daocommons.sorting.{ Field, SortBy }
import baile.daocommons.{ EntityDao, WithId }
import baile.utils.TryExtensions._
import org.bson.types.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.{ BsonDocument, BsonValue }
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Sorts._
import org.mongodb.scala.model.UpdateOneModel
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.{ Document, FindObservable, MongoCollection, MongoDatabase }

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag
import scala.util.{ Success, Try }

trait MongoEntityDao[T] extends EntityDao[T] with ReactiveStreamsConvertions {

  override type Predicate = Bson

  val collectionName: String

  override def create(prepareEntity: String => T)(implicit ec: ExecutionContext): Future[WithId[T]] = {
    val id = new ObjectId
    val entity = prepareEntity(id.toString)
    collection.insertOne(entityToDocument(entity) + ("_id" -> id)).toFuture().map(_ => WithId[T](entity, id.toString))
  }

  override def createMany(entities: Seq[T])(implicit ec: ExecutionContext): Future[Seq[String]] =
    entities match {
      case Seq() =>
        Future.successful(Seq.empty)
      case entities =>
        val ids: Seq[ObjectId] = entities.map(_ => new ObjectId)
        val documents = entities.zip(ids).map {
          case (entity, id) => entityToDocument(entity) + ("_id" -> id)
        }
        collection.insertMany(documents).toFuture().map(_ => ids.map(_.toString))
    }

  override def get(filter: Filter)(implicit ec: ExecutionContext): Future[Option[WithId[T]]] =
    executeWithPredicate(
      filter,
      predicate => {
        for {
          elem <- collection.find(predicate).headOption
          withId <- elem match {
            case Some(document) => documentToWithId(document).toFuture.map(Some(_))
            case None => Future.successful(None)
          }
        } yield withId
      }
    )

  override def list(
    filter: Filter,
    pageNumber: Int,
    pageSize: Int,
    sortBy: Option[SortBy]
  )(implicit ec: ExecutionContext): Future[Seq[WithId[T]]] =
    listQueryHelper(
      filter,
      limits = Some((pageSize * (pageNumber - 1), pageSize)),
      sortBy
    )

  override def listAll(
    filter: Filter,
    sortBy: Option[SortBy]
  )(implicit ec: ExecutionContext): Future[Seq[WithId[T]]] =
    listQueryHelper(
      filter,
      limits = None,
      sortBy
    )

  override def count(filter: Filter)(implicit ec: ExecutionContext): Future[Int] =
    executeWithPredicate(
      filter,
      predicate => collection
        .countDocuments(predicate)
        .toFuture
        .map(_.toInt)
    )

  override def updateMany(filter: Filter, updater: T => T)(implicit ec: ExecutionContext): Future[Int] = {

    def executeUpdates(currentWithIds: Seq[WithId[T]]): Future[Int] = {
      val entityUpdates = currentWithIds.map { case WithId(currentEntity, id) =>
        val updatedEntity = updater(currentEntity)
        val fieldUpdates = entityToDocument(updatedEntity).map { case (name, value) =>
          set(name, value)
        }.toSeq

        UpdateOneModel(Document("_id" -> new ObjectId(id)), combine(fieldUpdates: _*))
      }

      // We're returning matched count here to indicate how many items were AFFECTED by the update,
      // though not all of them might be actually modified
      if (entityUpdates.nonEmpty) collection.bulkWrite(entityUpdates).toFuture.map(_.getMatchedCount)
      else Future.successful(0)
    }

    for {
      currentWithIds <- listAll(filter)
      updatedCount <- executeUpdates(currentWithIds)
    } yield updatedCount
  }

  override def deleteMany(filter: Filter)(implicit ec: ExecutionContext): Future[Int] =
    executeWithPredicate(
      filter,
      predicate => collection
        .deleteMany(predicate)
        .toFuture
        .map(_.getDeletedCount.toInt)
    )

  protected val database: MongoDatabase

  protected def entityToDocument(entity: T): Document

  protected def documentToEntity(document: Document): Try[T]

  protected val fieldMapper: Map[Field, String]

  protected final def collection: MongoCollection[Document] =
    database.getCollection(collectionName)

  protected final def basicFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
    case And(left, right) =>
      for {
        left <- buildPredicate(left)
        right <- buildPredicate(right)
      } yield and(left, right)
    case Or(left, right) =>
      for {
        left <- buildPredicate(left)
        right <- buildPredicate(right)
      } yield or(left, right)
    case Not(filter) =>
      buildPredicate(filter).map(not)
    case TrueFilter =>
      Success(BsonDocument())
    case FalseFilter =>
      Success(BsonDocument("_id" -> BsonDocument("$exists" -> false)))
    case IdIs(id) =>
      if (ObjectId.isValid(id)) Try(equal("_id", new ObjectId(id)))
      else basicFilterMapper(FalseFilter)
    case IdIn(ids) =>
      Try(in("_id", ids.filter(ObjectId.isValid).map(id => new ObjectId(id)): _*))
  }

  protected final def buildSorting(sortBy: SortBy): Try[Bson] = Try {
    val sorts = sortBy.fields.foldLeft[List[Bson]](List()) {
      case (soFar, (field, direction)) =>
        fieldMapper.get(field) match {
          case Some(fieldName) =>
            val next = direction match {
              case Ascending => ascending(fieldName)
              case Descending => descending(fieldName)
            }
            soFar :+ next
          case None => throw UnknownSortingFieldException(field, this)
        }
    }
    orderBy(sorts: _*)
  }

  protected final def documentToWithId(document: Document): Try[WithId[T]] =
    for {
      entity <- documentToEntity(document)
      id <- Try(document.getObjectId("_id").toString)
    } yield WithId(entity, id)

  /** Returns the distinct values for a specified field.
   *
   * If the value of the specified field is an array,
   * this method considers each element of the array as a separate value (see
   * [[https://docs.mongodb.com/manual/reference/method/db.collection.distinct/#array-fields MongoDB Reference]]
   * for details).
   */
  protected def listDistinctValues[A, TResult <: BsonValue](
    fieldName: String,
    filter: Filter,
    fromBson: TResult => A
  )(implicit ec: ExecutionContext, ct: ClassTag[TResult]): Future[Seq[A]] =
    for {
      result <- executeWithPredicate(
        filter,
        predicate => collection.distinct[TResult](fieldName, predicate).toFuture
      )
    } yield result.map(fromBson)

  private def listQueryHelper(
    filter: Filter,
    limits: Option[(Int, Int)],
    sortBy: Option[SortBy]
  )(implicit ec: ExecutionContext): Future[Seq[WithId[T]]] = {

    def prepareQuery(predicate: Predicate): Try[FindObservable[Document]] = {
      val baseQuery = collection.find(predicate)

      val sortedQuery = sortBy match {
        case Some(value) => buildSorting(value).map(baseQuery.sort)
        case None => Success(baseQuery)
      }

      limits match {
        case Some((skip, limit)) => sortedQuery.map(_.skip(skip).limit(limit))
        case None => sortedQuery
      }
    }

    executeWithPredicate(
      filter,
      predicate => {
        for {
          query <- prepareQuery(predicate).toFuture
          withIdTries <- query.map(documentToWithId).toFuture
          entities <- Try.sequence(withIdTries).toFuture
        } yield entities
      }
    )
  }

  private def executeWithPredicate[R](
    filter: Filter,
    handler: Predicate => Future[R]
  )(implicit ec: ExecutionContext): Future[R] =
    for {
      predicate <- buildPredicate(filter).toFuture
      result <- handler(predicate)
    } yield result

}
