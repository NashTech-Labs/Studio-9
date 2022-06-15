package baile.routes.images

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import baile.routes.AuthenticatedRoutes
import baile.services.common.{ AuthenticationService, FileUploadService }
import baile.services.images.{ AlbumService, ImagesUploadService, PictureService }
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

class ImagesRoutes(
  conf: Config,
  val authenticationService: AuthenticationService,
  albumService: AlbumService,
  pictureService: PictureService,
  uploadService: ImagesUploadService,
  fileUploadService: FileUploadService
)(implicit val ec: ExecutionContext) extends AuthenticatedRoutes {
  private val albumRoutes = new AlbumRoutes(conf, albumService)
  private val pictureRoutes = new PictureRoutes(conf, pictureService, fileUploadService)
  private val uploadRoutes = new UploadRoutes(uploadService, fileUploadService)
  private val exportRoutes = new ExportRoutes(pictureService)

  val routes: Route =
    authenticated { authParams =>
      albumRoutes.routes()(authParams.user) ~
      pathPrefix("albums" / Segment) { albumId =>
        uploadRoutes.routes(albumId)(authParams.user) ~
        pictureRoutes.routes(albumId)(authParams.user)
      }
    } ~
    authenticatedWithQueryParam { authParams =>
      pathPrefix("albums" / Segment) { albumId =>
        exportRoutes.routes(albumId)(authParams.user)
      }
    }
}
