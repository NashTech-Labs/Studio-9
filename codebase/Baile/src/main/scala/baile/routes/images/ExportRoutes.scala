package baile.routes.images

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import baile.domain.usermanagement.User
import baile.routes.BaseRoutes
import baile.routes.contract.common.ErrorResponse
import baile.services.images.PictureService
import baile.services.images.PictureService.PictureServiceExportError

private class ExportRoutes(
  pictureService: PictureService
) extends BaseRoutes {

  def routes(albumId: String)(implicit user: User): Route =
    path("export") {
      (get & parameters('shared_resource_id.?)) { sharedResourceId =>
        onSuccess(pictureService.exportLabels(albumId, sharedResourceId)) {
          case Right(data) => completeWithFile(data, s"album-$albumId.csv", ContentTypes.`text/csv(UTF-8)`)
          case Left(error) => complete(translateError(error))
        }
      }
    }

  private def translateError(error: PictureServiceExportError): (StatusCode, ErrorResponse) = {
    error match {
      case PictureServiceExportError.AlbumNotFound =>
        errorResponse(StatusCodes.NotFound, "Album not found")
      case PictureServiceExportError.AccessDenied =>
        errorResponse(StatusCodes.Forbidden, "Access denied")
      case PictureServiceExportError.AlbumLabelModeNotSupported =>
        errorResponse(StatusCodes.BadRequest, "Label mode of the album is not supported")
    }
  }
}
