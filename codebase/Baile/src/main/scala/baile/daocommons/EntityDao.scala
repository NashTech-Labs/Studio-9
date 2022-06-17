package baile.daocommons

import baile.daocommons.exceptions.UnknownFilterException
import baile.daocommons.filters.{ Filter, IdIs }
import baile.daocommons.sorting.SortBy

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Try }

trait EntityDao[T] {

  type Predicate

  def create(entity: T)(implicit ec: ExecutionContext): Future[String] = create(_ => entity).map(_.id)

  def create(prepareEntity: String => T)(implicit ec: ExecutionContext): Future[WithId[T]]

  def createMany(entities: Seq[T])(implicit ec: ExecutionContext): Future[Seq[String]]

  def get(filter: Filter)(implicit ec: ExecutionContext): Future[Option[WithId[T]]]

  def get(id: String)(implicit ec: ExecutionContext): Future[Option[WithId[T]]] = get(IdIs(id))

  def list(
    filter: Filter,
    pageNumber: Int,
    pageSize: Int,
    sortBy: Option[SortBy] = None
  )(implicit ec: ExecutionContext): Future[Seq[WithId[T]]]

  def listAll(filter: Filter, sortBy: Option[SortBy] = None)(implicit ec: ExecutionContext): Future[Seq[WithId[T]]]

  def count(filter: Filter)(implicit ec: ExecutionContext): Future[Int]

  def exists(filter: Filter)(implicit ec: ExecutionContext): Future[Boolean] =
    count(filter).map(_ > 0)

  def updateMany(filter: Filter, updater: T => T)(implicit ec: ExecutionContext): Future[Int]

  def update(id: String, updater: T => T)(implicit ec: ExecutionContext): Future[Option[WithId[T]]] =
    for {
      updated <- updateMany(IdIs(id), updater)
      result <- {
        if (updated > 0) get(IdIs(id))
        else Future.successful(None)
      }
    } yield result

  def deleteMany(filter: Filter)(implicit ec: ExecutionContext): Future[Int]

  def delete(id: String)(implicit ec: ExecutionContext): Future[Boolean] = deleteMany(IdIs(id)).map(_ > 0)

  protected def basicFilterMapper: PartialFunction[Filter, Try[Predicate]]

  protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]]

  protected final def buildPredicate(filter: Filter): Try[Predicate] = {
    val filterMapper = basicFilterMapper orElse specificFilterMapper
    filterMapper.lift(filter.simplify).getOrElse(Failure(UnknownFilterException(filter, this)))
  }

}
