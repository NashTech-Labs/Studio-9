package baile.services.onlinejob

import baile.daocommons.WithId
import baile.domain.common.S3Bucket.{ AccessOptions, IdReference }
import baile.domain.cv.model.{ CVModel, CVModelStatus, CVModelType }
import baile.domain.images.{ Album, AlbumLabelMode, AlbumType }
import baile.domain.onlinejob.OnlinePredictionOptions
import baile.domain.usermanagement.User
import baile.services.argo.ArgoService
import baile.services.common.S3BucketService
import baile.services.common.S3BucketService.BucketDereferenceError
import baile.services.cv.model.{ CVModelCommonService, CVModelService }
import baile.services.cv.model.CVModelService.CVModelServiceError
import baile.services.images.{ AlbumService, ImagesCommonService }
import baile.services.onlinejob.OnlinePredictionConfigurator.OnlinePredictionConfiguratorError
import baile.services.onlinejob.OnlinePredictionConfigurator.OnlinePredictionConfiguratorError.BucketError
import baile.services.onlinejob.exceptions._
import baile.utils.TryExtensions._
import cats.data.EitherT
import cats.implicits._
import com.typesafe.config.Config
import cortex.api.argo.ConfigSetting
import cortex.api.taurus.{ S3ImagesSourceSettings, StreamSettings }
import play.api.libs.json.Json

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class OnlinePredictionConfigurator(
  conf: Config,
  argoService: ArgoService,
  imagesCommonService: ImagesCommonService,
  cvModelCommonService: CVModelCommonService,
  albumService: AlbumService,
  modelService: CVModelService,
  s3BucketService: S3BucketService
)(implicit val ec: ExecutionContext) {

  private val argoServiceName = "online-prediction"

  def configure(
    options: OnlinePredictionCreateOptions
  )(implicit user: User): Future[Either[OnlinePredictionConfiguratorError, OnlinePredictionOptions]] = {

    def getStreamId: Try[String] = Try {
      if (conf.hasPath("online-prediction.streamid")) conf.getString("online-prediction.streamid")
      else throw StreamIdNotFoundException()
    }

    def buildStreamSettings(
      album: WithId[Album],
      cortexId: String,
      accessOptions: AccessOptions,
      streamId: String
    ): StreamSettings =

      StreamSettings(
        id = streamId,
        s3Settings = S3ImagesSourceSettings(
          awsRegion = accessOptions.region,
          awsAccessKey = accessOptions.accessKey.getOrElse(""),
          awsSecretKey = accessOptions.secretKey.getOrElse(""),
          awsSessionToken = accessOptions.sessionToken,
          bucketName = accessOptions.bucketName,
          imagesPath = options.inputImagesPath
        ),
        modelId = cortexId,
        albumId = album.id,
        owner = user.id.toString,
        targetPrefix = imagesCommonService.getImagesPathPrefix(album.entity)
      )

    def setArgoSettings(streamSettings: StreamSettings, streamId: String): Future[ConfigSetting] = {
      val settingKey = s"stream_${ streamId }"
      argoService.setConfigValue(
        argoServiceName,
        settingKey,
        Json.toJson(streamSettings).toString,
        tags = List(settingKey)
      )
    }

    def createAlbum(labelMode: AlbumLabelMode): Future[WithId[Album]] = {
      albumService.create(
        name = options.outputAlbumName,
        labelMode = labelMode,
        albumType = AlbumType.Derived,
        inLibrary = true,
        ownerId = user.id
      )
    }

    def validateModelTypeAndGetLabelMode(model: CVModel): Either[OnlinePredictionConfiguratorError, AlbumLabelMode] =
      model.`type` match {
        case CVModelType.TL(_: CVModelType.TLConsumer.Classifier, _) => AlbumLabelMode.Classification.asRight
        case _ => OnlinePredictionConfiguratorError.InvalidModelType.asLeft
      }

    def validateModelStatus(cvModel: WithId[CVModel]): Either[OnlinePredictionConfiguratorError, Unit] = {
      cvModel.entity.status match {
        case CVModelStatus.Active => ().asRight
        case _ => OnlinePredictionConfiguratorError.ModelNotActive.asLeft
      }
    }

    val result = for {
      bucket <- EitherT(s3BucketService.dereferenceBucket(IdReference(options.bucketId))).leftMap(BucketError(_))
      model <- EitherT(modelService.get(id = options.modelId.toString)).leftMap(translateCVModelError)
      labelMode <- EitherT.fromEither[Future](validateModelTypeAndGetLabelMode(model.entity))
      _ <- EitherT.fromEither[Future](validateModelStatus(model))
      cortexId <- EitherT.right[OnlinePredictionConfiguratorError](
        cvModelCommonService.getCortexModelId(model).toFuture
      )
      streamId <- EitherT.right[OnlinePredictionConfiguratorError](getStreamId.toFuture)
      album <- EitherT.right[OnlinePredictionConfiguratorError](createAlbum(labelMode))
      streamSettings = buildStreamSettings(album, cortexId, bucket, streamId)
      _ <- EitherT.right[OnlinePredictionConfiguratorError](setArgoSettings(streamSettings, streamId))
    } yield OnlinePredictionOptions(
      streamId = streamId,
      modelId = options.modelId,
      bucketId = options.bucketId,
      inputImagesPath = options.inputImagesPath,
      outputAlbumId = album.id
    )
    result.value
  }

  private def translateCVModelError(error: CVModelServiceError): OnlinePredictionConfiguratorError = {
    error match {
      case CVModelServiceError.AccessDenied => OnlinePredictionConfiguratorError.AccessDenied
      case CVModelServiceError.ModelNotFound => OnlinePredictionConfiguratorError.ModelNotFound
      case error =>
        throw UnexpectedResponseException(s"Got unexpected response from CVModelService: '${ error }'")
    }
  }

}

object OnlinePredictionConfigurator {

  sealed trait OnlinePredictionConfiguratorError

  object OnlinePredictionConfiguratorError {
    case class BucketError(error: BucketDereferenceError) extends OnlinePredictionConfiguratorError
    case object AccessDenied extends OnlinePredictionConfiguratorError
    case object ModelNotFound extends OnlinePredictionConfiguratorError
    case object ModelNotActive extends OnlinePredictionConfiguratorError
    case object InvalidModelType extends OnlinePredictionConfiguratorError
  }

}
