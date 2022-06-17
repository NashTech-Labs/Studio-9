package baile.routes.experiment

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives.{ complete, concat, get, parameters, pathEnd, pathPrefix, _ }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.MarshallingDirectives.{ as, entity }
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.domain.asset.AssetScope
import baile.domain.experiment.Experiment
import baile.domain.usermanagement.User
import baile.routes.contract.asset.AssetScopeReads
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.experiment._
import baile.routes.pipeline.PipelineErrorHandling
import baile.routes.{ AuthenticatedRoutes, WithAssetProcessRoute }
import baile.services.common.AuthenticationService
import baile.services.cv.CVTLModelPrimitiveService.CVTLModelPrimitiveServiceError
import baile.services.cv.model.CVModelTrainPipelineHandler.CVModelCreateError
import baile.services.cv.model.CVModelTrainPipelineHandler.CVModelCreateError._
import baile.services.experiment.ExperimentService
import baile.services.experiment.ExperimentService.ExperimentServiceError
import baile.services.pipeline.GenericExperimentPipelineHandler.{ GenericPipelineCreateError, UntappedStepInputs }
import baile.services.tabular.model.TabularTrainPipelineHandler.TabularModelCreateError
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

class ExperimentRoutes(
  val conf: Config,
  val authenticationService: AuthenticationService,
  val service: ExperimentService
)(implicit ec: ExecutionContext) extends AuthenticatedRoutes
  with WithAssetProcessRoute[Experiment] with PipelineErrorHandling {

  val routes: Route = authenticated { authParams =>
    implicit val user: User = authParams.user
    pathPrefix("experiments") {
      concat(
        pathEnd {
          concat(
            parameters((
              'scope.as[AssetScope](fromStringUnmarshaller[AssetScope]).?,
              'search.?,
              'page.as[Int].?,
              'page_size.as[Int].?,
              'order.as(CsvSeq[String]).?,
              'projectId.?,
              'folderId.?
            )) {
              (scope, search, page, page_size, order, projectId, folderId) =>
                get {
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
                    case Right((list, count)) => complete(ListResponse(list.map(ExperimentResponse.fromDomain), count))
                    case Left(error) => complete(translateError(error))
                  }
                }
            },
            (post & entity(as[ExperimentCreateRequest])) { request =>
              val data = service.create(
                request.name,
                request.description,
                request.pipeline.toDomain
              )
              onSuccess(data) {
                case Right(response) => complete(ExperimentFullResponse.fromDomain(response))
                case Left(error) => complete(translateError(error))
              }
            }
          )
        },
        pathPrefix(Segment) { experimentId =>
          pathEnd {
            (get & parameters('shared_resource_id.?)) { sharedResourceId =>
              onSuccess(service.get(experimentId, sharedResourceId)) {
                case Right(in) => complete(ExperimentFullResponse.fromDomain(in))
                case Left(error) => complete(translateError(error))
              }
            } ~
            delete {
              onSuccess(service.delete(experimentId)) {
                case Right(_) => complete(IdResponse(experimentId))
                case Left(error) => complete(translateError(error))
              }
            } ~
            (put & entity(as[ExperimentUpdateRequest])) { request =>
              val data = service.update(
                experimentId,
                request.name,
                request.description
              )
              onSuccess(data) {
                case Right(experimentWithId) => complete(ExperimentFullResponse.fromDomain(experimentWithId))
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          processRoute(experimentId)
        }
      )
    }

  }

  def translateError(error: ExperimentServiceError): (StatusCode, ErrorResponse) = error match {
    case ExperimentServiceError.AccessDenied => errorResponse(StatusCodes.Forbidden, "Access denied")
    case ExperimentServiceError.EmptyExperimentName =>
      errorResponse(StatusCodes.BadRequest, "Experiment name must not be empty")
    case ExperimentServiceError.NameNotSpecified =>
      errorResponse(StatusCodes.BadRequest, "Experiment name not specified")
    case ExperimentServiceError.ExperimentNotFound =>
      errorResponse(StatusCodes.NotFound, "Experiment not found")
    case ExperimentServiceError.NameIsTaken =>
      errorResponse(StatusCodes.Conflict, "Provided experiment name is already taken")
    case ExperimentServiceError.SortingFieldUnknown =>
      errorResponse(StatusCodes.BadRequest, "Sorting field not known")
    case ExperimentServiceError.ExperimentInUse =>
      errorResponse(StatusCodes.BadRequest, "Experiment in use")
    case ExperimentServiceError.ExperimentError(pipelineError) => pipelineError match {
      case error: CVModelCreateError => translateError(error)
      case error: TabularModelCreateError => translateError(error)
      case error: GenericPipelineCreateError => translateError(error)
    }
  }

  def translateError(error: CVModelCreateError): (StatusCode, ErrorResponse) =
    errorResponse(StatusCodes.BadRequest, error match {
      case AlbumNotFound(id) => s"Album $id not found"
      case AlbumNotActive(id) => s"Album $id is not active"
      case NoPicturesInAlbum(albumId) => s"Album $albumId contains no picture"
      case InvalidAugmentationRequestParamError(message) => s"Invalid Augmentation Request Params: $message"
      case FeatureExtractorNotInLibrary => "Feature Extractor is not in library"
      case FeatureExtractorNotFound => "Feature Extractor not found"
      case AlbumLabelModeNotCompatible => "Album label mode provided is not compatible"
      case ArchitectureNotSupported => "Architecture not supported"
      case ModelTypeIsIncompatibleWithAlbum => "Model type is incompatible with album"
      case ConsumerIsIncompatibleWithArchitecture => "Consumer is incompatible with architecture"
      case InvalidCVModelType => "Invalid CV Model type"
      case InvalidParams => "Invalid Params provided"
      case OutputAlbumNotFound => "Output album not found"
      case ModelNotFound => "Model not found"
      case CantDeleteRunningModel => "Can't delete running model"
      case InvalidArchitecture => "Invalid architecture"
      case ModelLearningRateNotPositive => "Model learning rate must be positive"
      case FeatureExtractorLearningRateNotPositive => "Feature extractor learning rate must be positive"
      case ParameterNotFound(name) => s"Pipeline parameter: [$name] not found"
      case CVTLModelPrimitiveError(error) => error match {
        case CVTLModelPrimitiveServiceError.NotFound(id) => s"CV TL primitive $id not found"
        case CVTLModelPrimitiveServiceError.AccessDenied(id) => s"Access denied to CV TL primitive $id"
      }
    })

  def translateError(error: TabularModelCreateError): (StatusCode, ErrorResponse) =
    errorResponse(StatusCodes.BadRequest, error match {
      case TabularModelCreateError.TableNotFound(id) => s"Table $id not found"
      case TabularModelCreateError.TableNotActive(id) => s"Table $id is not active"
      case TabularModelCreateError.ColumnNotFoundInTable(columnName, tableId) =>
        s"Table $tableId does not have column $columnName"
      case TabularModelCreateError.InvalidColumnDataType(column, allowedTypes) =>
        s"Column $column can not have this type. The only allowed types are ${ allowedTypes.mkString("\n") }"
    })

  def translateError(error: GenericPipelineCreateError): (StatusCode, ErrorResponse) = error match {
    case GenericPipelineCreateError.GenericPipelineStepValidationError(pipelineError) =>
      translateError(pipelineError)
    case GenericPipelineCreateError.NotAllInputsAreConnected(untappedStepInputs) =>
      errorResponse(
        StatusCodes.BadRequest,
        untappedStepInputs.map {
          case UntappedStepInputs(stepId, operatorName, inputNames) =>
            s"Step $stepId ($operatorName) has inputs that not connected: ${ inputNames.mkString(", ") }"
        }.mkString("; ")
      )
    case GenericPipelineCreateError.ParameterNotProvided(operatorId, operatorName, paramName) =>
      errorResponse(
        StatusCodes.BadRequest,
        s"Operator $operatorName (#$operatorId) requires parameter $paramName to be set"
      )
    case GenericPipelineCreateError.InvalidAssetReferenceValueType(operatorId, operatorName, paramName) =>
      errorResponse(
        StatusCodes.BadRequest,
        s"Operator $operatorName (#$operatorId) requires $paramName to be string Asset ID"
      )
    case GenericPipelineCreateError.GenericPipelineServiceError(serviceError) => translateError(serviceError)
  }
}
