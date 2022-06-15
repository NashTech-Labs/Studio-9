package baile.services.cv.prediction

import baile.dao.cv.prediction.CVPredictionDao
import baile.domain.asset.sharing.SharedResource
import baile.domain.asset.{ AssetReference, AssetType }
import baile.services.asset.sharing.SharedAccessChecker
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.Future

trait CVPredictionSharedAccess extends SharedAccessChecker {

  protected val cvPredictionDao: CVPredictionDao

  abstract override def checkSharedAccess(
    assetReference: AssetReference,
    sharedResource: SharedResource
  ): EitherT[Future, Unit, Unit] =
    super.checkSharedAccess(assetReference, sharedResource) orElse {
      sharedResource.assetType match {
        case AssetType.CvPrediction =>
          for {
            prediction <- EitherT.fromOptionF(cvPredictionDao.get(sharedResource.assetId), ())
            result <- assetReference.`type` match {
              case AssetType.Album => accessGrantedIf(
                Seq(
                  prediction.entity.inputAlbumId,
                  prediction.entity.outputAlbumId
                ).contains(assetReference.id)
              )
              case AssetType.CvModel => accessGrantedIf(
                prediction.entity.modelId == assetReference.id
              )
              case _ => accessDenied
            }
          } yield result
        case _ => accessDenied
      }
    }
}
