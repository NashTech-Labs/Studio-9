package baile.services.cv.model

import baile.dao.cv.model.CVModelDao
import baile.domain.asset.sharing.SharedResource
import baile.domain.asset.{ AssetReference, AssetType }
import baile.services.asset.sharing.SharedAccessChecker
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.Future

trait CVModelSharedAccess extends SharedAccessChecker {

  protected val cvModelDao: CVModelDao

  abstract override def checkSharedAccess(
    assetReference: AssetReference,
    sharedResource: SharedResource
  ): EitherT[Future, Unit, Unit] =
    super.checkSharedAccess(assetReference, sharedResource) orElse {
      sharedResource.assetType match {
        case AssetType.CvModel => assetReference.`type` match {
          case AssetType.CvModel => checkFeatureExtractorAccess(sharedResource.assetId, assetReference.id)
          case _ => accessDenied
        }
        case _ => accessDenied
      }
    }

  final protected def checkFeatureExtractorAccess(
    modelId: String,
    featureExtractorId: String
  ): EitherT[Future, Unit, Unit] =
    for {
      cvModel <- EitherT.fromOptionF(cvModelDao.get(modelId), ())
      result <- accessGrantedIf(
        cvModel.entity.featureExtractorId.contains(featureExtractorId)
      )
    } yield result
}
