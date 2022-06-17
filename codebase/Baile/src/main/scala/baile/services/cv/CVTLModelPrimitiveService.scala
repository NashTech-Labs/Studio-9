package baile.services.cv

import baile.dao.cv.model.tlprimitives.CVTLModelPrimitiveDao
import baile.dao.cv.model.tlprimitives.CVTLModelPrimitiveDao.{ CVTLModelPrimitiveTypeIs, PackageIdIn }
import baile.daocommons.WithId
import baile.daocommons.filters.{ IdIs, TrueFilter }
import baile.domain.common.Version
import baile.domain.cv.model.CVModelType
import baile.domain.cv.model.CVModelType.TLConsumer
import baile.domain.cv.model.tlprimitives.{ CVTLModelPrimitive, CVTLModelPrimitiveType }
import baile.domain.dcproject.DCProjectPackage
import baile.domain.images.AlbumLabelMode
import baile.domain.pipeline.OperatorParameter
import baile.domain.usermanagement.User
import baile.services.cv.CVTLModelPrimitiveService.CVTLModelPrimitiveServiceError.{ AccessDenied, NotFound }
import baile.services.cv.CVTLModelPrimitiveService.{ CVTLModelPrimitiveServiceError, ExtendedCVTLModelPrimitive }
import baile.services.dcproject.DCProjectPackageService
import baile.services.dcproject.DCProjectPackageService.DCProjectPackageServiceError
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

class CVTLModelPrimitiveService(
  cvModelTLPrimitiveDao: CVTLModelPrimitiveDao,
  packageService: DCProjectPackageService
)(implicit val ec: ExecutionContext) {

  def getCVTLModelPrimitivesWithPackageInfo(
    cvTLModelPrimitiveType: CVTLModelPrimitiveType
  )(implicit user: User): Future[Seq[ExtendedCVTLModelPrimitive]] = {
    for {
      // TODO: use aggregations to avoid retrieving all packages
      packages <- EitherT(packageService.listAll(TrueFilter, Nil)).valueOr(error =>
        throw new RuntimeException(s"Error occurred during retrieving a list of CV TL primitives: $error")
      )
      cvTLModelPrimitives <- cvModelTLPrimitiveDao.listAll(
        CVTLModelPrimitiveTypeIs(cvTLModelPrimitiveType) && PackageIdIn(packages.map(_.id))
      )
    } yield cvTLModelPrimitives.map { cvTLModelPrimitive =>
      val packageInfo = packages.find(_.id == cvTLModelPrimitive.entity.packageId).getOrElse(
        throw new RuntimeException(s"Unexpectedly not found package ${ cvTLModelPrimitive.entity.packageId }")
      )

      ExtendedCVTLModelPrimitive(
        id = cvTLModelPrimitive.id,
        name = cvTLModelPrimitive.entity.name,
        moduleName = cvTLModelPrimitive.entity.moduleName,
        className = cvTLModelPrimitive.entity.className,
        packageName = packageInfo.entity.name,
        packageVersion = packageInfo.entity.version,
        isNeural = cvTLModelPrimitive.entity.isNeural,
        params = cvTLModelPrimitive.entity.params
      )
    }
  }

  private def tlConsumerIsCompatibleWithLabelMode(
    albumLabelMode: AlbumLabelMode,
    consumer: CVModelType.TLConsumer
  ): Boolean = {
    (consumer, albumLabelMode) match {
      case (_: TLConsumer.Classifier, AlbumLabelMode.Classification) => true
      case (_: TLConsumer.Localizer, AlbumLabelMode.Localization) => true
      case (_: TLConsumer.Decoder, _) => true
      case _ => false
    }
  }

  private[cv] def validateAlbumAndTLConsumerCompatibility[E](
    albumLabelMode: AlbumLabelMode,
    consumer: CVModelType.TLConsumer,
    error: E
  ): Either[E, Unit] =
    Either.cond(
      tlConsumerIsCompatibleWithLabelMode(albumLabelMode, consumer),
      (),
      error
    )

  private[cv] def validateAlbumAndModelCompatibility[E](
    albumLabelMode: AlbumLabelMode,
    modelType: CVModelType,
    error: E
  ): Either[E, Unit] =
    Either.cond(
      modelType match {
        case CVModelType.TL(consumer, _) => tlConsumerIsCompatibleWithLabelMode(albumLabelMode, consumer)
        case CVModelType.Custom(_, labelMode) => labelMode.forall(_ == albumLabelMode)
      },
      (),
      error
    )

  private[cv] def getModelPrimitiveWithPackage(
    cvTLModelPrimitiveId: String
  )(
    implicit user: User
  ): Future[Either[CVTLModelPrimitiveServiceError, (WithId[CVTLModelPrimitive], WithId[DCProjectPackage])]] = {
    val result = for {
      primitive <- EitherT.fromOptionF(cvModelTLPrimitiveDao.get(cvTLModelPrimitiveId), NotFound(cvTLModelPrimitiveId))
      projectPackage <- EitherT(packageService.get(primitive.entity.packageId)).leftMap {
        case DCProjectPackageServiceError.AccessDenied =>
          AccessDenied(cvTLModelPrimitiveId): CVTLModelPrimitiveServiceError
        case unexpectedError => throw new RuntimeException(
          s"Unexpectedly cannot get package ${ primitive.entity.packageId }: $unexpectedError"
        )
      }
    } yield (primitive, projectPackage)

    result.value
  }

  private[cv] def loadTLConsumerPrimitive(consumer: CVModelType.TLConsumer): Future[WithId[CVTLModelPrimitive]] = {
    loadCVTLModelPrimitive(consumer.operatorId, getCVTLModelPrimitiveType(consumer))
  }

  private[cv] def loadFeatureExtractorArchitecturePrimitive(
    architecturePrimitiveId: String
  ): Future[WithId[CVTLModelPrimitive]] = loadCVTLModelPrimitive(architecturePrimitiveId, CVTLModelPrimitiveType.UTLP)

  private def loadCVTLModelPrimitive(
    cvTLModelPrimitiveId: String,
    cvTLModelPrimitiveType: CVTLModelPrimitiveType
  ): Future[WithId[CVTLModelPrimitive]] = {
    cvModelTLPrimitiveDao.get(IdIs(cvTLModelPrimitiveId) && CVTLModelPrimitiveTypeIs(cvTLModelPrimitiveType)) map {
      _.getOrElse(throw new RuntimeException(s"Unexpectedly not found cv tl model primitive $cvTLModelPrimitiveId"))
    }
  }

  private[cv] def getCVTLModelPrimitiveType(consumer: CVModelType.TLConsumer): CVTLModelPrimitiveType = {
    consumer match {
      case _: CVModelType.TLConsumer.Classifier => CVTLModelPrimitiveType.Classifier
      case _: CVModelType.TLConsumer.Localizer => CVTLModelPrimitiveType.Detector
      case _: CVModelType.TLConsumer.Decoder => CVTLModelPrimitiveType.Decoder
    }
  }

  private[cv] def validateModelTypeAndCVTLModelPrimitiveType[E](
    consumer: CVModelType.TLConsumer,
    cvTLModelPrimitiveType: CVTLModelPrimitiveType,
    error: E
  ): Either[E, Unit] = {
    Either.cond(
      (consumer, cvTLModelPrimitiveType) match {
        case (_: CVModelType.TLConsumer.Classifier, CVTLModelPrimitiveType.Classifier) => true
        case (_: CVModelType.TLConsumer.Localizer, CVTLModelPrimitiveType.Detector) => true
        case (_: CVModelType.TLConsumer.Decoder, CVTLModelPrimitiveType.Decoder) => true
        case _ => false
      },
      (),
      error
    )
  }

  private[cv] def validateFEArchitectureCVTLModelPrimitiveType[E](
    cvTLModelPrimitiveType: CVTLModelPrimitiveType,
    error: E
  ): Either[E, Unit] = {
    Either.cond(
      cvTLModelPrimitiveType == CVTLModelPrimitiveType.UTLP,
      (),
      error
    )
  }

}

object CVTLModelPrimitiveService {

  case class ExtendedCVTLModelPrimitive(
    id: String,
    name: String,
    moduleName: String,
    className: String,
    packageName: String,
    packageVersion: Option[Version],
    isNeural: Boolean,
    params: Seq[OperatorParameter]
  )

  case class CVTLModelPrimitiveReference(
    operator: WithId[CVTLModelPrimitive],
    packageLocation: Option[String]
  )

  sealed trait CVTLModelPrimitiveServiceError

  object CVTLModelPrimitiveServiceError {
    case class NotFound(id: String) extends CVTLModelPrimitiveServiceError
    case class AccessDenied(id: String) extends CVTLModelPrimitiveServiceError
  }

}
