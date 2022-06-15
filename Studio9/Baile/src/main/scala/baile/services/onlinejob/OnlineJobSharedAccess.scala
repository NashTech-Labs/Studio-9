package baile.services.onlinejob

import baile.dao.onlinejob.OnlineJobDao
import baile.daocommons.WithId
import baile.domain.asset.sharing.SharedResource
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.onlinejob.{ OnlineJob, OnlinePredictionOptions }
import baile.services.asset.sharing.SharedAccessChecker
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.Future

trait OnlineJobSharedAccess extends SharedAccessChecker {

  protected val onlineJobDao: OnlineJobDao

  abstract override def checkSharedAccess(
    assetReference: AssetReference,
    sharedResource: SharedResource
  ): EitherT[Future, Unit, Unit] =
    super.checkSharedAccess(assetReference, sharedResource) orElse {
      sharedResource.assetType match {
        case AssetType.OnlineJob => for {
          onlineJob <- EitherT.fromOptionF(onlineJobDao.get(sharedResource.assetId), ())
          onlinePredictionOptions <- EitherT.fromOption[Future](getOnlinePredictionJobOptions(onlineJob), ())
          result <- assetReference.`type` match {
            case AssetType.Album => accessGrantedIf(onlinePredictionOptions.outputAlbumId.contains(assetReference.id))
            case AssetType.CvModel => accessGrantedIf(onlinePredictionOptions.modelId.contains(assetReference.id))
            case _ => accessDenied
          }
        } yield result
        case _ => accessDenied
      }
    }

  private def getOnlinePredictionJobOptions(onlineJob: WithId[OnlineJob]): Option[OnlinePredictionOptions] =
    onlineJob.entity.options match {
      case onlinePredictionOptions: OnlinePredictionOptions => Some(onlinePredictionOptions)
      case _ => None
    }

}
