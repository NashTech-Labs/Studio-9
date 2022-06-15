package baile.routes.tabular.prediction

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.domain.asset.AssetScope
import baile.routes.contract.asset.AssetScopeReads
import baile.domain.tabular.prediction.{ ColumnMapping, TabularPrediction }
import baile.domain.usermanagement.User
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.tabular.prediction._
import baile.routes.{ AuthenticatedRoutes, WithAssetProcessRoute }
import baile.services.common.AuthenticationService
import baile.services.tabular.model.TabularModelService
import baile.services.tabular.prediction.TabularPredictionService
import baile.services.tabular.prediction.TabularPredictionService._
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

class TabularPredictionRoutes(
  val conf: Config,
  val authenticationService: AuthenticationService,
  val service: TabularPredictionService,
  val tabularModelService: TabularModelService
)(implicit ec: ExecutionContext) extends AuthenticatedRoutes with WithAssetProcessRoute[TabularPrediction] {

  val routes: Route = authenticated { authParams =>
    implicit val user: User = authParams.user
    pathPrefix("predictions") {
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
            case Right((list, count)) => complete(ListResponse(list.map(TabularPredictionResponse.fromDomain), count))
            case Left(error) => complete(translateError(error))
          }
        } ~
        (post & entity(as[TabularPredictionCreateRequest])) { predictRequest =>
          onSuccess(service.create(
            predictRequest.name,
            predictRequest.modelId,
            predictRequest.input,
            predictRequest.columnMappings map { columnMappingPair =>
              ColumnMapping(
                trainName = columnMappingPair.sourceColumn,
                currentName = columnMappingPair.mappedColumn
              )
            },
            predictRequest.description
          )) {
            case Right(response) => complete(TabularPredictionResponse.fromDomain(response))
            case Left(error) => complete(translateError(error))
          }
        }
      } ~
      pathPrefix(Segment) { tabularPredictionId =>
        pathEnd {
          (get & parameters('shared_resource_id.?)) { sharedResourceId =>
            onSuccess(service.get(tabularPredictionId, sharedResourceId)) {
              case Right(prediction) => complete(TabularPredictionResponse.fromDomain(prediction))
              case Left(error) => complete(translateError(error))
            }
          } ~
          put {
            entity(as[TabularPredictionUpdateRequest]) { update =>
              onSuccess(service.update(tabularPredictionId, update.name, update.description)) {
                case Right(prediction) => complete(TabularPredictionResponse.fromDomain(prediction))
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          delete {
            onSuccess(service.delete(tabularPredictionId)) {
              case Right(_) => complete(IdResponse(tabularPredictionId))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        path("save") {
          post {
            onSuccess(service.save(tabularPredictionId)) {
              case Right(_) => complete(IdResponse(tabularPredictionId))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        processRoute(tabularPredictionId)
      }
    }
  }

  private def translateError(error: TabularPredictionServiceError): (StatusCode, ErrorResponse) = {
    error match {
      case TabularPredictionServiceError.NotFound =>
        errorResponse(StatusCodes.NotFound, "Tabular prediction not found")
      case TabularPredictionServiceError.AccessDenied =>
        errorResponse(StatusCodes.Forbidden, "Access denied")
      case TabularPredictionServiceError.SortingFieldUnknown =>
        errorResponse(StatusCodes.BadRequest, "Sorting field not known")
      case TabularPredictionServiceError.TableNotFound =>
        errorResponse(StatusCodes.BadRequest, "Table not found for tabular prediction")
      case TabularPredictionServiceError.TabularPredictionNameAlreadyExists(name) =>
        errorResponse(StatusCodes.BadRequest, s"Prediction with name $name already exists")
      case TabularPredictionServiceError.PredictionNameCanNotBeEmpty =>
        errorResponse(StatusCodes.BadRequest, "Prediction name can not be empty")
      case TabularPredictionServiceError.PredictionNotComplete =>
        errorResponse(StatusCodes.BadRequest, "Prediction is not completed")
      case TabularPredictionServiceError.TabularPredictionInUse =>
        errorResponse(StatusCodes.BadRequest, "Prediction in use")
    }
  }

  private def translateError(error: TabularPredictionCreateError): (StatusCode, ErrorResponse) = {
    error match {
      case TabularPredictionCreateError.TabularPredictionNameAlreadyExists(name) =>
        errorResponse(StatusCodes.BadRequest, s"Prediction with name $name already exists")
      case TabularPredictionCreateError.PredictionNameCanNotBeEmpty =>
        errorResponse(StatusCodes.BadRequest, "Prediction name can not be empty")
      case TabularPredictionCreateError.NameNotSpecified =>
        errorResponse(StatusCodes.BadRequest, "Prediction name not specified")
      case TabularPredictionCreateError.TabularModelNotActive =>
        errorResponse(StatusCodes.BadRequest, "Tabular Model provided is not active")
      case TabularPredictionCreateError.TabularModelNotFound =>
        errorResponse(StatusCodes.BadRequest, "Tabular Model not found for cv prediction")
      case TabularPredictionCreateError.ColumnMappingNotFound =>
        errorResponse(StatusCodes.BadRequest, "Column mapping list cannot be empty")
      case TabularPredictionCreateError.PredictorColumnMissing(column) =>
        errorResponse(StatusCodes.BadRequest, s"Column mapping for predictor ${ column.name } not found")
      case TabularPredictionCreateError.PredictorNotFoundInTable(column, table) =>
        errorResponse(StatusCodes.BadRequest, s"Predictor ${ column.currentName } not found in table ${ table.name }")
      case TabularPredictionCreateError.InvalidColumnDataType(column, allowedConversions) =>
        errorResponse(
          StatusCodes.BadRequest,
          s"${ column.name } data type ${ column.dataType } can not be converted to $allowedConversions"
        )
      case TabularPredictionCreateError.TableNotFound =>
        errorResponse(StatusCodes.BadRequest, "Table not found for creating tabular prediction")
      case TabularPredictionCreateError.ColumnMappingWithInvalidPredictor =>
        errorResponse(StatusCodes.BadRequest, "Column mapping for predictor that does not exists")
      case TabularPredictionCreateError.ColumnMappingNotUnique =>
        errorResponse(StatusCodes.BadRequest, "Same column is mapped for more than one predictor")
    }
  }

}
