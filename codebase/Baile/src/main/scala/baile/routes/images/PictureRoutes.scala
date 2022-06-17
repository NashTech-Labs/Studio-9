package baile.routes.images

import akka.http.scaladsl.model.{ Multipart, StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives.{ path, _ }
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.directives.BasicDirectives.extractRequestContext
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.MarshallingDirectives.{ as, entity }
import akka.http.scaladsl.server.{ MissingFormFieldRejection, Route }
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.domain.images.augmentation.AugmentationType
import baile.domain.usermanagement.User
import baile.routes.WithFileUploading.MultipartFileHandlingError.{ PartIsMissing, UploadedFileHandlingFailed }
import baile.routes.contract.common.IdResponse._
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.images._
import baile.routes.contract.images.augmentation.AugmentationTypeFormat
import baile.routes.{ BaseRoutes, WithFileUploading }
import baile.services.common.FileUploadService
import baile.services.images.PictureService
import baile.services.images.PictureService.PictureServiceError
import cats.data.EitherT
import cats.implicits._
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

private class PictureRoutes(
  conf: Config,
  pictureService: PictureService,
  val fileUploadService: FileUploadService
)(implicit ec: ExecutionContext) extends BaseRoutes with WithFileUploading {

  // scalastyle:off method.length
  def routes(albumId: String)(implicit user: User): Route =
    pathPrefix("pictures") {
      pathEnd {
        (get & parameters((
          'search.?,
          'page.as[Int].?,
          'page_size.as[Int].?,
          'labels.as(CsvSeq[String]).?,
          'shared_resource_id.?,
          'order.as(CsvSeq[String]).?,
          'augmentations.as(CsvOptionSeq[AugmentationType]).?
        ))) { (search, page, page_size, labels, sharedResourceId, order, augmentationTypes) =>
          val data = for {
            picturesWithCount <- EitherT(pictureService.list(
              albumId = albumId,
              labels = labels,
              search = search,
              orderBy = order.getOrElse(Seq.empty),
              page = page.getOrElse(1),
              pageSize = page_size.getOrElse(conf.getInt("routes.default-page-size")),
              sharedResourceId = sharedResourceId,
              augmentationTypes = augmentationTypes
            ))
            (pictures, count) = picturesWithCount
            signedPictures <- EitherT(pictureService.signPictures(albumId, pictures, sharedResourceId))
          } yield (signedPictures, count)

          onSuccess(data.value) {
            case Right((list, count)) => complete(ListResponse(list.map(PictureResponse.fromDomain), count))
            case Left(error) => complete(translateError(error))
          }
        } ~
        (put & entity(as[AddPicturesRequest])) { addPicturesRequest =>
          val data = pictureService.addPictures(
            albumId = albumId,
            pictures = addPicturesRequest.toDomain(
              albumId
            ),
            keepExisting = addPicturesRequest.keepExisting
          )

          onSuccess(data) {
            case Right(_) => complete(IdResponse(albumId))
            case Left(error) => complete(translateError(error))
          }
        }
      } ~
      path(Segment) { id =>
        (get & parameters('shared_resource_id.?)) { sharedResourceId =>
          val data = for {
            pictureWithId <- EitherT(pictureService.get(
              albumId = albumId,
              pictureId = id,
              sharedResourceId = sharedResourceId
            ))
            picture <- EitherT(pictureService.signPicture(albumId, pictureWithId, sharedResourceId))
          } yield picture

          onSuccess(data.value) {
            case Right(pictureWithId) => complete(PictureResponse.fromDomain(pictureWithId))
            case Left(error) => complete(translateError(error))
          }
        } ~
        (put & entity(as[PictureUpdateRequest])) { pictureUpdateRequest =>
          val data = for {
            updatedPicture <- EitherT(pictureService.update(
              albumId,
              id,
              pictureUpdateRequest.caption,
              pictureUpdateRequest.tags
            ))
            picture <- EitherT(pictureService.signPicture(albumId, updatedPicture))
          } yield picture

          onSuccess(data.value) {
            case Right(picture) => complete(PictureResponse.fromDomain(picture))
            case Left(error) => complete(translateError(error))
          }
        } ~
        delete {
          onSuccess(pictureService.delete(albumId, id)) {
            case Right(_) => complete(IdResponse(id))
            case Left(error) => complete(translateError(error))
          }
        }
      }
    } ~
    path("tags") {
      (get & parameters('shared_resource_id.?)) { sharedResourceId =>
        onSuccess(pictureService.getLabelsStats(albumId, sharedResourceId)) {
          case Right(stats) => complete(AlbumTagsSummaryResponse.fromDomain(stats))
          case Left(error) => complete(translateError(error))
        }
      }
    } ~
    path("uploadPicture") {
      (post & withoutSizeLimit) {
        extractRequestContext { ctx =>
          import ctx.materializer
          entity(as[Multipart.FormData]) { data =>
            onSuccess(withUploadFile(
              data,
              "file",
              (filePath, fileInfo, params) => pictureService.create(
                albumId = albumId,
                pictureName = params("filename"),
                uploadedFilePath = filePath,
                fileName = fileInfo.fileName
              ),
              validateFormFieldsPresent("filename")
            )) {
              case Right(pictureWithId) => complete(PictureResponse.fromDomain(pictureWithId))
              case Left(PartIsMissing(partName)) => reject(MissingFormFieldRejection(partName))
              case Left(UploadedFileHandlingFailed(error)) => complete(translateError(error))
            }
          }
        }
      }
    }
  // scalastyle:on method.length

  private def translateError(error: PictureServiceError): (StatusCode, ErrorResponse) = {
    error match {
      case PictureServiceError.AlbumNotFound =>
        errorResponse(StatusCodes.NotFound.intValue, "Album not found")
      case PictureServiceError.PictureNotFound =>
        errorResponse(StatusCodes.NotFound.intValue, "Picture not found")
      case PictureServiceError.AccessDenied =>
        errorResponse(StatusCodes.Forbidden.intValue, "Access denied")
      case PictureServiceError.PictureOperationUnavailable(reason) =>
        errorResponse(StatusCodes.Conflict.intValue, reason)
      case PictureServiceError.PictureTypeUnknown =>
        errorResponse(StatusCodes.Conflict.intValue, "Picture format unsupported")
      case PictureServiceError.SortingFieldUnknown =>
        errorResponse(StatusCodes.BadRequest.intValue, "Sorting field not known")
      case PictureServiceError.PicturesNotFound =>
        errorResponse(StatusCodes.BadRequest.intValue, "Pictures not found")
    }
  }

}
