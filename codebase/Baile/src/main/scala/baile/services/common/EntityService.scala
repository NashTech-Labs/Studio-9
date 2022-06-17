package baile.services.common

import akka.event.LoggingAdapter
import baile.daocommons.{ EntityDao, WithId }
import baile.daocommons.filters.{ Filter, TrueFilter }
import baile.daocommons.sorting.{ Direction, Field, SortBy }
import baile.domain.usermanagement.User
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

trait EntityService[T, F] {
  implicit val logger: LoggingAdapter
  implicit val ec: ExecutionContext

  val notFoundError: F

  protected val dao: EntityDao[T]

  def get(id: String)(implicit user: User): Future[Either[F, WithId[T]]] = {
    val result = for {
      withIdOption <- EitherT.right[F](dao.get(id))
      withId <- EitherT.fromEither[Future](ensureEntityFound(withIdOption))
      _ <- EitherT(ensureCanRead(withId, user))
    } yield withId

    result.value
  }

  def listAll(
    filter: Filter,
    orderBy: Seq[String]
  )(implicit user: User): Future[Either[F, Seq[WithId[T]]]] = {
    val result = for {
      canReadFilter <- EitherT.right[F](prepareCanReadFilter(user))
      sortBy <- EitherT.fromEither[Future](prepareSortBy(orderBy))
      items <- EitherT.right[F](dao.listAll(canReadFilter && filter, sortBy))
    } yield items

    result.value
  }

  def list(
    filter: Filter,
    orderBy: Seq[String],
    page: Int,
    pageSize: Int
  )(implicit user: User): Future[Either[F, (Seq[WithId[T]], Int)]] = {
    val result = for {
      canReadFilter <- EitherT.right[F](prepareCanReadFilter(user))
      sortBy <- EitherT.fromEither[Future](prepareSortBy(orderBy))
      items <- EitherT.right[F](dao.list(canReadFilter && filter, page, pageSize, sortBy))
      count <- EitherT.right[F](dao.count(canReadFilter && filter))
    } yield (items, count)

    result.value
  }

  def count(
    filter: Filter
  )(implicit user: User): Future[Either[F, Int]] = {
    val result = for {
      canReadFilter <- EitherT.right[F](prepareCanReadFilter(user))
      count <- EitherT.right[F](dao.count(canReadFilter && filter))
    } yield count

    result.value
  }

  def delete(id: String)(implicit user: User): Future[Either[F, Unit]] = {
    val result = for {
      withIdOption <- EitherT.right[F](dao.get(id))
      withId <- EitherT.fromEither[Future](ensureEntityFound(withIdOption))
      _ <- EitherT(ensureCanDelete(withId, user))
      _ <- EitherT(preDelete(withId))
      _ <- EitherT.right[F](dao.delete(id))
    } yield ()

    result.value
  }

  def update(id: String, updater: T => T)(implicit user: User): Future[Either[F, WithId[T]]] =
    update(id, _ => Future.successful(().asRight), updater)

  def update(
    id: String,
    validator: WithId[T] => Future[Either[F, Unit]],
    updater: T => T
  )(implicit user: User): Future[Either[F, WithId[T]]] = {
    val result = for {
      withIdOption <- EitherT.right[F](dao.get(id))
      withId <- EitherT.fromEither[Future](ensureEntityFound(withIdOption))
      _ <- EitherT(ensureCanUpdate(withId, user))
      _ <- EitherT(validator(withId))
      updateResult <- EitherT.right[F](dao.update(id, updater))
      updatedWithId <- EitherT.rightT[Future, F](updateResult.getOrElse(
        throw EntityUpdateFailedException(id, withId.entity)
      ))
    } yield updatedWithId

    result.value
  }

  protected def preDelete(entity: WithId[T])(implicit user: User): Future[Either[F, Unit]] =
    Future.successful(().asRight)

  protected final def ensureEntityFound(withIdOption: Option[WithId[T]]): Either[F, WithId[T]] =
    Either.fromOption(withIdOption, notFoundError)

  protected def ensureCanRead(withId: WithId[T], user: User): Future[Either[F, Unit]] =
    Future.successful(().asRight)

  protected def ensureCanUpdate(withId: WithId[T], user: User): Future[Either[F, Unit]] =
    Future.successful(().asRight)

  protected def ensureCanDelete(withId: WithId[T], user: User): Future[Either[F, Unit]] =
    Future.successful(().asRight)

  protected def prepareCanReadFilter(user: User): Future[Filter] =
    Future.successful(TrueFilter)

  protected def prepareSortBy(
    terms: Seq[String],
    findField: String => Option[Field] = _ => None
  ): Either[F, Option[SortBy]] =
    None.asRight

}

object EntityService {
  trait WithSortByField[T, F] {
    self: EntityService[T, F] =>
    val sortingFieldNotFoundError: F
    protected val findField: String => Option[Field]
    protected val defaultSortBy: Option[SortBy] = None

    override protected def prepareSortBy(
      terms: Seq[String],
      findField: String => Option[Field] = findField
    ): Either[F, Option[SortBy]] = {
      def explodeTerm(term: String): (String, Direction) = term.head match {
        case '-' => (term.tail, Direction.Descending)
        case _ => (term, Direction.Ascending)
      }

      def recursor(terms: Seq[String], fields: Seq[(Field, Direction)]): Either[F, SortBy] =
        if (terms.isEmpty) SortBy(fields: _*).asRight
        else {
          val (fieldName, direction) = explodeTerm(terms.head)
          findField(fieldName) match {
            case Some(field) => recursor(terms.tail, fields :+ ((field, direction)))
            case None => sortingFieldNotFoundError.asLeft
          }
        }

      if (terms.isEmpty) defaultSortBy.asRight
      else recursor(terms, Seq.empty).map(Some(_))
    }
  }
}
