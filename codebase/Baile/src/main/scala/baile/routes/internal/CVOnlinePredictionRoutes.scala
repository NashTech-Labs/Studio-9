package baile.routes.internal

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import baile.routes.BaseRoutes
import baile.routes.contract.common.ErrorResponse
import baile.services.cv.online.CVOnlinePredictionService
import baile.services.cv.online.CVOnlinePredictionService.CVOnlinePredictionServiceError
import com.typesafe.config.Config
import cortex.api.baile.SavePredictionResultRequest

class CVOnlinePredictionRoutes(
  conf: Config,
  cvOnlinePredictionService: CVOnlinePredictionService
) extends BaseRoutes {


  val routes: Route = path("cv-online-prediction" / Segment) { albumId: String =>
    (post & entity(as[SavePredictionResultRequest])) { request =>
      onSuccess(cvOnlinePredictionService.insertPredictedPictures(albumId, request.results)) {
        case Right(_) => complete(StatusCodes.OK -> "OK")
        case Left(e) => complete(translateError(e))
      }
    }
  }

  def translateError(error: CVOnlinePredictionServiceError): (StatusCode, ErrorResponse) = error match {
    case CVOnlinePredictionService.AlbumNotFoundError(albumId) =>
      (StatusCodes.NotFound, ErrorResponse(StatusCodes.NotFound.intValue, s"Album $albumId not found"))
    case CVOnlinePredictionService.AlbumNotForPredictionError(albumId) =>
      (StatusCodes.BadRequest, ErrorResponse(StatusCodes.NotFound.intValue, s"Album $albumId is not for prediction"))
  }
}
