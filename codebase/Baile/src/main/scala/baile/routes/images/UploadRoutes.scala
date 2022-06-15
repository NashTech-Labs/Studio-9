package baile.routes.images

import java.io.File

import akka.http.scaladsl.model.{ HttpResponse, Multipart, StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ MissingFormFieldRejection, Route }
import akka.http.scaladsl.server.directives.FileInfo
import baile.domain.common.S3Bucket
import baile.domain.usermanagement.User
import baile.routes.WithFileUploading.MultipartFileHandlingError.{ PartIsMissing, UploadedFileHandlingFailed }
import baile.routes.{ BaseRoutes, WithDereferenceBucketError, WithFileUploading }
import baile.routes.contract.common.ErrorResponse
import baile.routes.contract.images.{ AlbumResponse, _ }
import baile.services.common.FileUploadService
import baile.services.images.ImagesUploadService
import baile.services.images.ImagesUploadService.ImagesUploadServiceError

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

private class UploadRoutes(
  service: ImagesUploadService,
  val fileUploadService: FileUploadService
)(implicit ec: ExecutionContext) extends BaseRoutes with WithDereferenceBucketError with WithFileUploading {

  // scalastyle:off method.length
  def routes(albumId: String)(implicit user: User): Route =
    path("importPicturesFromS3") {
      (post & toStrictEntity(60.seconds)) {
        // uploading labels file
        tempUploadFile("file") { (_: FileInfo, labelsFile: File) =>
          s3BucketOptions { s3Options =>
            formFields((
              'S3ImagesPath.as[String],
              'applyLogTransformation.as[Boolean].?
            )) { (S3ImagesPath, applyLogTransformation) =>
              onSuccess(service.importImagesFromS3(
                albumId,
                s3Options,
                S3ImagesPath,
                None,
                Some(labelsFile),
                applyLogTransformation
              )) {
                case Right(album) => complete(StatusCodes.Accepted -> AlbumResponse.fromDomain(album))
                case Left(error) => complete(translateError(error))
              }
            }
          }
        } ~
        s3BucketOptions { s3Options => // TODO: deprecate and change UI
          formFields((
            'S3ImagesPath.as[String],
            'S3CSVPath.as[String].?,
            'applyLogTransformation.as[Boolean].?
          )) { (S3ImagesPath, S3CSVPath, applyLogTransformation) =>
            onSuccess(service.importImagesFromS3(
              albumId,
              s3Options,
              S3ImagesPath,
              S3CSVPath,
              None,
              applyLogTransformation
            )) {
              case Right(album) => complete(StatusCodes.Accepted -> AlbumResponse.fromDomain(album))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        entity(as[ImportImagesFromS3Request]) { request =>
          onSuccess(service.importImagesFromS3(
            albumId,
            request.bucket.toDomain,
            request.imagesPath,
            request.labelsCSVPath,
            None,
            request.applyLogTransformation
          )) {
            case Right(album) => complete(StatusCodes.Accepted -> AlbumResponse.fromDomain(album))
            case Left(error) => complete(translateError(error))
          }
        }
      }
    } ~
    path("importVideoFromS3") {
      (post & entity(as[ImportVideoFromS3Request])) { request =>
        onSuccess(service.importVideoFromS3(
          albumId,
          request.bucket.toDomain,
          request.videoPath,
          request.frameRateDivider
        )) {
          case Right(album) => complete(StatusCodes.OK -> AlbumResponse.fromDomain(album))
          case Left(error) => complete(translateError(error))
        }
      } ~
      complete(StatusCodes.BadRequest -> ErrorResponse(StatusCodes.BadRequest.intValue, "Invalid request"))
    } ~
    path("importLabelsFromS3") {
      (post & entity(as[ImportLabelsFromS3Request])) { request =>
        extractMaterializer { implicit materializer =>
          withRequestTimeoutResponse(_ => HttpResponse(StatusCodes.Accepted)) {
            onSuccess(service.importLabelsFromCSVFileS3(albumId, request.bucket.toDomain, request.csvPath)) {
              case Right(album) => complete(StatusCodes.OK -> AlbumResponse.fromDomain(album))
              case Left(error) => complete(translateError(error))
            }
          }
        }
      }
    } ~
    path("uploadLabels") {
      post {
        extractMaterializer { implicit materializer =>
          entity(as[Multipart.FormData]) { data =>
            onSuccess(withUploadStream(
              data,
              "file",
              (fileSource, _, _) =>
                service.importLabelsFromCSVFile(
                  albumId,
                  fileSource
                )
            )) {
              case Left(PartIsMissing(partName)) => reject(MissingFormFieldRejection(partName))
              case Left(UploadedFileHandlingFailed(error)) => complete(translateError(error))
              case Right(album) => complete(AlbumResponse.fromDomain(album))
            }
          }
        }
      }
    }
  // scalastyle:on method.length

  private def s3BucketOptions(f: S3Bucket => Route): Route = {
    formFields(
      'AWSS3BucketId.as[String]
    ) { bucketId =>
      f(S3Bucket.IdReference(bucketId))
    } ~ formFields((
      'AWSRegion.as[String],
      'AWSS3BucketName.as[String],
      'AWSAccessKey.as[String],
      'AWSSecretKey.as[String],
      'AWSSessionToken.as[String]
    )) { (AWSRegion, AWSS3BucketName, AWSAccessKey, AWSSecretKey, AWSSessionToken) =>
      f(S3Bucket.AccessOptions(
        region = AWSRegion,
        bucketName = AWSS3BucketName,
        accessKey = Some(AWSAccessKey),
        secretKey = Some(AWSSecretKey),
        sessionToken = Some(AWSSessionToken)
      ))
    }
  }

  private def translateError(error: ImagesUploadServiceError): (StatusCode, ErrorResponse) = error match {
    case ImagesUploadServiceError.AlbumNotFoundError(albumId) =>
      errorResponse(StatusCodes.NotFound, s"Album $albumId not found")
    case ImagesUploadServiceError.BucketError(bucketError) =>
      translateError(bucketError)
    case ImagesUploadServiceError.ErrorReadingS3File(reason) =>
      errorResponse(StatusCodes.BadRequest, s"Error reading file from S3. $reason")
    case ImagesUploadServiceError.AlbumLabelModeNotSupported =>
      errorResponse(StatusCodes.BadRequest, "Label mode of the album is not supported")
    case ImagesUploadServiceError.PictureOperationUnavailable(reason) =>
      errorResponse(StatusCodes.Conflict, s"Can not perform the operation for pictures because $reason")
    case ImagesUploadServiceError.VideoUploadUnavailable(reason) =>
      errorResponse(StatusCodes.Conflict, s"Can not perform the operation for video because $reason")
  }
}
