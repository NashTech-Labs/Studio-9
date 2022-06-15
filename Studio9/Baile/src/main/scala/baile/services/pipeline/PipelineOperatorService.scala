package baile.services.pipeline

import akka.event.LoggingAdapter
import baile.dao.dcproject.DCProjectPackageDao.{ NameIs, VersionIs }
import baile.dao.pipeline.PipelineOperatorDao
import baile.dao.pipeline.PipelineOperatorDao.{ ClassNameIs, ModuleNameIs }
import baile.daocommons.WithId
import baile.daocommons.filters.{ Filter, TrueFilter }
import baile.daocommons.sorting.Field
import baile.domain.common.Version
import baile.domain.dcproject.DCProjectPackage
import baile.domain.pipeline.PipelineOperator
import baile.domain.usermanagement.User
import baile.services.common.EntityService
import baile.services.common.EntityService.WithSortByField
import baile.services.dcproject.DCProjectPackageService
import baile.services.pipeline.PipelineOperatorService.PipelineOperatorServiceError._
import baile.services.pipeline.PipelineOperatorService.{ PipelineOperatorInfo, PipelineOperatorServiceError }
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

class PipelineOperatorService(
  protected val dao: PipelineOperatorDao,
  packageService: DCProjectPackageService
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends EntityService[PipelineOperator, PipelineOperatorServiceError]
  with WithSortByField[PipelineOperator, PipelineOperatorServiceError] {

  val forbiddenError: PipelineOperatorServiceError = AccessDenied
  override val sortingFieldNotFoundError: PipelineOperatorServiceError = SortingFieldUnknown
  override val notFoundError: PipelineOperatorServiceError = PipelineOperatorNotFound
  override protected val findField: String => Option[Field] = Map.empty[String, Field].get

  def getPipelineOperator(
    id: String
  )(implicit user: User): Future[Either[PipelineOperatorServiceError, PipelineOperatorInfo]] = {
    val result = for {
      operator <- EitherT(get(id))
      dcProjectPackage <- EitherT(packageService.get(operator.entity.packageId))
        .leftMap(_ => AccessDenied: PipelineOperatorServiceError)
    } yield PipelineOperatorInfo(operator, dcProjectPackage)
    result.value
  }

  def list(
    orderBy: Seq[String],
    page: Int,
    pageSize: Int,
    moduleName: Option[String],
    className: Option[String],
    packageName: Option[String],
    packageVersion: Option[Version]
  )(implicit user: User): Future[Either[PipelineOperatorServiceError, (Seq[PipelineOperatorInfo], Int)]] = {
    val moduleNameFilter = moduleName.fold[Filter](TrueFilter)(ModuleNameIs(_))
    val classNameFilter = className.fold[Filter](TrueFilter)(ClassNameIs(_))
    val packageNameFilter = packageName.fold[Filter](TrueFilter)(NameIs(_))
    val packageVersionFilter = packageVersion.fold[Filter](TrueFilter)(VersionIs(_))
    val result = for {
      packages <- EitherT(packageService.listAll(packageNameFilter && packageVersionFilter, Nil))
        .leftMap(_ => AccessDenied)
      operatorsAndCount <- EitherT(list(
        PipelineOperatorDao.PackageIdIn(packages.map(_.id)) && moduleNameFilter && classNameFilter,
        orderBy,
        page,
        pageSize
      ))
    } yield {
      val (operators, count) = operatorsAndCount
      val operatorInfo = operators map { operator =>
        val projectPackage = packages.find(_.id == operator.entity.packageId)
          .getOrElse(throw new RuntimeException(s"Unexpectedly not found package ${ operator.entity.packageId }"))
        PipelineOperatorInfo(operator, projectPackage)
      }
      (operatorInfo, count)
    }
    result.value
  }

}

object PipelineOperatorService {

  case class PipelineOperatorInfo(operator: WithId[PipelineOperator], dcProjectPackage: WithId[DCProjectPackage])

  sealed trait PipelineOperatorServiceError

  object PipelineOperatorServiceError {

    case object PipelineOperatorNotFound extends PipelineOperatorServiceError

    case object AccessDenied extends PipelineOperatorServiceError

    case object SortingFieldUnknown extends PipelineOperatorServiceError

  }

}
