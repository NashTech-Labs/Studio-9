package baile.services.asset

import akka.event.LoggingAdapter
import baile.daocommons.{ EntityDao, WithId }
import baile.domain.asset.AssetType
import baile.domain.usermanagement.User
import baile.services.asset.SampleAssetService.SampleAssetError
import baile.services.project.ProjectService

import scala.concurrent.{ ExecutionContext, Future }

class SampleAssetService(
  val dao: EntityDao[SampleAsset],
  val projectService: ProjectService
)(implicit val logger: LoggingAdapter, val ec: ExecutionContext) extends AssetService[SampleAsset, SampleAssetError] {

  override val assetType: AssetType = AssetType.Table
  override val notFoundError: SampleAssetError = SampleAssetError.AssetNotFound
  override val forbiddenError: SampleAssetError = SampleAssetError.AccessDenied

  override protected[services] def preDelete(
    asset: WithId[SampleAsset]
  )(implicit user: User): Future[Either[SampleAssetError, Unit]] =
    Future.successful(Right(()))
}

object SampleAssetService {

  sealed trait SampleAssetError

  object SampleAssetError {
    case object AssetNotFound extends SampleAssetError
    case object AccessDenied extends SampleAssetError
  }

}
