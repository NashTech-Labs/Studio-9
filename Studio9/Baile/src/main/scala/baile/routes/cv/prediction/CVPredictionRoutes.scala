package baile.routes.cv.prediction

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.domain.asset.AssetScope
import baile.domain.cv.prediction.CVPrediction
import baile.domain.usermanagement.User
import baile.routes.contract.asset.AssetScopeReads
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.cv.prediction.CVPredictionCreateRequest._
import baile.routes.contract.cv.prediction.CVPredictionResponse._
import baile.routes.contract.cv.prediction._
import baile.routes.{ AuthenticatedRoutes, WithAssetProcessRoute }
import baile.services.common.AuthenticationService
import baile.services.cv.prediction.CVPredictionService
import baile.services.cv.prediction.CVPredictionService.{ CVPredictionCreateError, CVPredictionServiceError }
import com.typesafe.config.Config

class CVPredictionRoutes(
  val conf: Config,
  val authenticationService: AuthenticationService,
  val service: CVPredictionService
) extends AuthenticatedRoutes with WithAssetProcessRoute[CVPrediction] {

  val routes: Route = authenticated { authParams =>
    implicit val user: User = authParams.user
    pathPrefix("cv-predictions") {
      pathEnd {
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
            case Right((list, count)) => complete(ListResponse(list.map(CVPredictionResponse.fromDomain), count))
            case Left(error) => complete(translateError(error))
          }
        } ~
        (post & entity(as[CVPredictionCreateRequest])) { predictRequest =>

          onSuccess(service.create(
            predictRequest.modelId,
            predictRequest.input,
            predictRequest.name,
            predictRequest.description,
            predictRequest.outputAlbumName,
            predictRequest.options.map(_.toDomain),
            predictRequest.evaluate.getOrElse(false)
          )) {
            case Right(response) => complete(CVPredictionResponse.fromDomain(response))
            case Left(error) => complete(translateCreateError(error))
          }
        }
      } ~
      pathPrefix(Segment) { cvPredictionId =>
        pathEnd {
          (get & parameters('shared_resource_id.?)) { sharedResourceId =>
            onSuccess(service.get(cvPredictionId, sharedResourceId)) {
              case Right(model) => complete(CVPredictionResponse.fromDomain(model))
              case Left(error) => complete(translateError(error))
            }
          } ~
          delete {
            onSuccess(service.delete(cvPredictionId)) {
              case Right(_) => complete(IdResponse(cvPredictionId))
              case Left(error) => complete(translateError(error))
            }
          } ~
          put {
            entity(as[CVPredictionUpdateRequest]) { update =>
              onSuccess(service.update(cvPredictionId, update.name, update.description)) {
                case Right(response) => complete(CVPredictionResponse.fromDomain(response))
                case Left(error) => complete(translateError(error))
              }
            }
          }
        } ~
        processRoute(cvPredictionId)
      }
    }
  }

  private def translateError(error: CVPredictionServiceError): (StatusCode, ErrorResponse) = {
    error match {
      case CVPredictionServiceError.NotFound =>
        errorResponse(StatusCodes.NotFound,"CV prediction not found")
      case CVPredictionServiceError.AccessDenied =>
        errorResponse(StatusCodes.Forbidden,"Access denied")
      case CVPredictionServiceError.SortingFieldUnknown =>
        errorResponse(StatusCodes.BadRequest,"Sorting field not known")
      case CVPredictionServiceError.AlbumNotFound =>
        errorResponse(StatusCodes.BadRequest,"Album not found for cv prediction")
      case CVPredictionServiceError.PredictionAlreadyExists =>
        errorResponse(StatusCodes.BadRequest, "Prediction already exists")
      case CVPredictionServiceError.PredictionNameCanNotBeEmpty =>
        errorResponse(StatusCodes.BadRequest, "Prediction name can not be empty")
      case CVPredictionServiceError.CVPredictionInUse =>
        errorResponse(StatusCodes.BadRequest, "Prediction in use")
    }
  }

  private def translateCreateError(error: CVPredictionCreateError): (StatusCode, ErrorResponse) = {
    error match {
      case CVPredictionCreateError.PredictionAlreadyExists =>
        errorResponse(StatusCodes.BadRequest, "Prediction already exists")
      case CVPredictionCreateError.PredictionNameCanNotBeEmpty =>
        errorResponse(StatusCodes.BadRequest, "Prediction name can not be empty")
      case CVPredictionCreateError.NameNotSpecified =>
        errorResponse(StatusCodes.BadRequest, "Prediction name not specified")
      case CVPredictionCreateError.CVModelNotActive =>
        errorResponse(StatusCodes.BadRequest, "CV Model provided is not active")
      case CVPredictionCreateError.CVModelNotFound =>
        errorResponse(StatusCodes.BadRequest, "CV Model not found for cv prediction")
      case CVPredictionCreateError.CVModelCantBeUsed =>
        errorResponse(StatusCodes.BadRequest, "This CV Model can be used only for transfer learning train")
      case CVPredictionCreateError.NoPicturesInAlbum =>
        errorResponse(StatusCodes.BadRequest, "No pictures were found in input album")
      case CVPredictionCreateError.AlbumNotFound =>
        errorResponse(StatusCodes.BadRequest,"Album not found for creating cv prediction")
      case CVPredictionCreateError.EmptyLabelsOfInterest =>
        errorResponse(StatusCodes.BadRequest,"No values for labels of interest were provided")
      case CVPredictionCreateError.UnsupportedAlbumLabelMode =>
        errorResponse(StatusCodes.BadRequest,"Album label mode not supported")
    }
  }

}
