package baile.routes.images

import akka.http.scaladsl.model.{ ContentTypes, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import baile.domain.usermanagement.User
import baile.routes.RoutesSpec
import baile.services.images.PictureService
import baile.services.images.PictureService.PictureServiceExportError
import baile.services.usermanagement.util.TestData.SampleUser
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.when
import play.api.libs.json.JsObject


class ExportRoutesSpec extends RoutesSpec {
  implicit val user: User = SampleUser
  val service: PictureService = mock[PictureService]

  private val albumId = randomString()

  val routes: Route = new ExportRoutes(service).routes(albumId)

  when(service.exportLabels(any[String], any[Option[String]])(any[User]))
    .thenReturn(future(Left(PictureServiceExportError.AlbumNotFound)))

  "GET /albums/:id/export" should {
    "return CSV data" in {
      when(service.exportLabels(eqTo(albumId), any[Option[String]])(any[User]))
        .thenReturn(future(Right(Source.empty)))

      Get("/export").signed.check(ContentTypes.`text/csv(UTF-8)`) {
        status shouldBe StatusCodes.OK
      }
    }

    "reject on unknown album" in {
      when(service.exportLabels(eqTo(albumId), any[Option[String]])(any[User]))
        .thenReturn(future(Left(PictureServiceExportError.AlbumNotFound)))

      Get("/export").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "reject on unsupported album" in {
      when(service.exportLabels(eqTo(albumId), any[Option[String]])(any[User]))
        .thenReturn(future(Left(PictureServiceExportError.AlbumLabelModeNotSupported)))

      Get("/export").signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "reject on forbidden album" in {
      when(service.exportLabels(eqTo(albumId), any[Option[String]])(any[User]))
        .thenReturn(future(Left(PictureServiceExportError.AccessDenied)))

      Get("/export").signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }
}
