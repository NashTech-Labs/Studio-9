package baile.routes.images

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.domain.asset.AssetScope
import baile.domain.images.{ Album, MergeAlbumsParams }
import baile.domain.usermanagement.User
import baile.routes.contract.asset.AssetScopeReads
import baile.routes.contract.common.IdResponse._
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.images._
import baile.routes.contract.images.augmentation.AugmentRequest
import baile.routes.{ BaseRoutes, WithAssetProcessRoute }
import baile.services.images.AlbumService
import baile.services.images.AlbumService.{ AlbumNameValidationError, AlbumServiceCreateError, AlbumServiceError }
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

private class AlbumRoutes(
  val conf: Config,
  val service: AlbumService
)(implicit ec: ExecutionContext) extends BaseRoutes with WithAssetProcessRoute[Album] {

  // scalastyle:off method.length
  def routes()(implicit user: User): Route =
    pathPrefix("albums") {
      pathEnd {
        (post & entity(as[AlbumCreateRequest])) { albumCreateReq =>
          val data = service.create(
            name = albumCreateReq.name,
            labelMode = albumCreateReq.labelMode,
            mergeParams = albumCreateReq.copyPicturesFrom.map(inputAlbumIds =>
              MergeAlbumsParams(inputAlbumIds, albumCreateReq.copyOnlyLabelledPictures.getOrElse(false))
            ),
            description = albumCreateReq.description,
            inLibrary = albumCreateReq.inLibrary
          )
          onSuccess(data) {
            case Right(albumWithId) => complete(AlbumResponse.fromDomain(service.signAlbum(albumWithId)))
            case Left(error) => complete(translateError(error))
          }
        } ~
        (get & parameters((
          'scope.as[AssetScope](fromStringUnmarshaller[AssetScope]).?,
          'search.?,
          'page.as[Int].?,
          'page_size.as[Int].?,
          'order.as(CsvSeq[String]).?,
          'projectId.?,
          'folderId.?
        ))) { (scope, search, page, page_size, order, projectId, folderId) =>
          val data = service.list(
            scope,
            search,
            order.getOrElse(Seq.empty),
            page.getOrElse(1),
            page_size.getOrElse(conf.getInt("routes.default-page-size")),
            projectId,
            folderId
          )
          onSuccess(data) {
            case Right((list, count)) => complete(ListResponse(
              list.map(service.signAlbum).map(AlbumResponse.fromDomain),
              count
            ))
            case Left(error) => complete(translateError(error))
          }
        }
      } ~
      pathPrefix(Segment) { id =>
        pathEnd {
          get {
            parameters('shared_resource_id.?) { sharedResourceId =>
              onSuccess(service.get(id, sharedResourceId)) {
                case Right(albumWithId) => complete(AlbumResponse.fromDomain(service.signAlbum(albumWithId)))
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          delete {
            onSuccess(service.delete(id)) {
              case Right(_) => complete(IdResponse(id))
              case Left(error) => complete(translateError(error))
            }
          } ~
          (put & entity(as[AlbumUpdateRequest])) { albumUpdateReq =>
            val data = service.update(
              id,
              albumUpdateReq.name,
              albumUpdateReq.description,
              albumUpdateReq.labelMode
            )

            onSuccess(data) {
              case Right(albumWithId) => complete(AlbumResponse.fromDomain(service.signAlbum(albumWithId)))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        path("copy") {
          (post & entity(as[AlbumCopyRequest])) { albumCopyRequest =>
            parameter('shared_resource_id.?) { sharedResourceId =>
              onSuccess(service.clone(
                albumId = id,
                selectedPictureIds = albumCopyRequest.pictureIds,
                newName = albumCopyRequest.name,
                newDescription = albumCopyRequest.description,
                copyOnlyTaggedPictures = albumCopyRequest.copyOnlyLabelledPictures.getOrElse(false),
                inLibrary = albumCopyRequest.inLibrary,
                sharedResourceId = sharedResourceId
              )) {
                case Right(albumWithId) => complete(AlbumResponse.fromDomain(service.signAlbum(albumWithId)))
                case Left(error) => complete(translateError(error))
              }
            }
          }
        } ~
        path("save") {
          (post & entity(as[AlbumSaveRequest])) { albumSaveRequest =>
            onSuccess(service.save(id, albumSaveRequest.name)) {
              case Right(albumWithId) => complete(AlbumResponse.fromDomain(albumWithId))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        path("augment") {
          (post & entity(as[AugmentRequest])) { augmentRequest =>
            onSuccess(service.augmentPictures(
              augmentationParams = augmentRequest.augmentations.map(_.toDomain),
              outputAlbumName = augmentRequest.outputName,
              inputAlbumId = id,
              includeOriginalPictures = augmentRequest.includeOriginalPictures,
              bloatFactor = augmentRequest.bloatFactor,
              inLibrary = augmentRequest.inLibrary
            )) {
              case Right(albumWithId) => complete(AlbumResponse.fromDomain(service.signAlbum(albumWithId)))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        path("access-param") {
          get {
            onSuccess(service.generateAlbumStorageAccessParams(id)) {
              case Right(albumStorageAccessParameters) =>
                complete(AlbumStorageAccessParametersResponse.fromDomain(albumStorageAccessParameters))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        processRoute(id)
      }
    }
  // scalastyle:on method.length

  private def translateError(error: AlbumServiceError): (StatusCode, ErrorResponse) = error match {
    case AlbumServiceError.AlbumNotFound =>
      errorResponse(StatusCodes.NotFound, "Album not found")
    case AlbumServiceError.AccessDenied =>
      errorResponse(StatusCodes.Forbidden, "Access denied")
    case AlbumServiceError.SortingFieldUnknown =>
      errorResponse(StatusCodes.BadRequest, "Sorting field not known")
    case AlbumServiceError.AlbumLabelModeLocked(reason) =>
      errorResponse(StatusCodes.BadRequest, s"You can not change label mode: $reason")
    case AlbumServiceError.AlbumDeleteUnavailable(reason) =>
      errorResponse(StatusCodes.Conflict, s"Can not delete album because $reason")
    case AlbumServiceError.AlbumIsNotActive =>
      errorResponse(StatusCodes.BadRequest, "This can be done only to active album")
    case AlbumServiceError.InvalidAugmentationRequestParamError(message) =>
      errorResponse(StatusCodes.BadRequest, message)
    case AlbumServiceError.AlbumInUse =>
      errorResponse(StatusCodes.BadRequest, "Album is in use")
    case validationError: AlbumNameValidationError => translateError(validationError)
  }

  private def translateError(error: AlbumServiceCreateError): (StatusCode, ErrorResponse) = error match {
    case AlbumServiceCreateError.AlbumsToMergeNotFound =>
      errorResponse(StatusCodes.BadRequest, "Some input album(s) not found")
    case AlbumServiceCreateError.AlbumsToMergeEmpty =>
      errorResponse(StatusCodes.BadRequest, "At least one album to merge required")
    case AlbumServiceCreateError.AlbumsToMergeIncorrectLabelMode =>
      errorResponse(StatusCodes.BadRequest, "Label Mode of merging albums does not match")
    case AlbumServiceCreateError.AlbumsToMergeHaveNoPictures =>
      errorResponse(StatusCodes.BadRequest, "Selected albums have no pictures")
    case validationError: AlbumNameValidationError => translateError(validationError)
  }

  private def translateError(error: AlbumNameValidationError): (StatusCode, ErrorResponse) = error match {
    case AlbumNameValidationError.AlbumNameTaken =>
      errorResponse(StatusCodes.BadRequest, "Album with this name already exists")
    case AlbumNameValidationError.AlbumNameIsEmpty =>
      errorResponse(StatusCodes.BadRequest, "Album name cannot be empty")
    case AlbumNameValidationError.NameNotSpecified =>
      errorResponse(StatusCodes.BadRequest, "Album name not specified")
  }
}
