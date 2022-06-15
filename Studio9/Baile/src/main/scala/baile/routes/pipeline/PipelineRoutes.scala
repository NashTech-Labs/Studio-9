package baile.routes.pipeline

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.routes.contract.asset.AssetScopeReads
import baile.domain.asset.AssetScope
import baile.domain.usermanagement.User
import baile.routes.{ AuthenticatedRoutes, BaseRoutes }
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.pipeline.{ PipelineCreateRequest, PipelineResponse, PipelineUpdateRequest }
import baile.services.common.AuthenticationService
import baile.services.pipeline.PipelineOperatorService.PipelineOperatorServiceError
import baile.services.pipeline.PipelineService.PipelineServiceError
import baile.services.pipeline.{ PipelineService, PipelineValidationError }
import com.typesafe.config.Config

class PipelineRoutes(
  val conf: Config,
  val authenticationService: AuthenticationService,
  val service: PipelineService
) extends AuthenticatedRoutes with PipelineErrorHandling {

  val routes: Route = authenticated { authParams =>
    implicit val user: User = authParams.user
    pathPrefix("pipelines") {
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
            case Right((list, count)) => complete(ListResponse(
              list.map(PipelineResponse.fromDomain),
              count
            ))
            case Left(error) => complete(translateError(error))
          }
        } ~
        (post & entity(as[PipelineCreateRequest])) { pipelineCreateRequest =>
          onSuccess(service.create(
            pipelineCreateRequest.name,
            pipelineCreateRequest.description,
            pipelineCreateRequest.inLibrary,
            pipelineCreateRequest.steps.map(_.toDomain)
          )) {
            case Right(pipelineWithId) => complete(PipelineResponse.fromDomain(pipelineWithId))
            case Left(error) => complete(translateError(error))
          }
        }
      } ~
      pathPrefix(Segment) { id =>
        pathEnd {
          (get & parameters('shared_resource_id.?)) { sharedResourceId =>
            onSuccess(service.get(id, sharedResourceId)) {
              case Right(pipelineWithId) => complete(PipelineResponse.fromDomain(pipelineWithId))
              case Left(error) => complete(translateError(error))
            }
          } ~
          (put & entity(as[PipelineUpdateRequest])) { pipelineUpdateRequest =>
            onSuccess(service.update(
              id,
              pipelineUpdateRequest.name,
              pipelineUpdateRequest.description,
              pipelineUpdateRequest.steps.map(_.map(_.toDomain))
            )) {
              case Right(pipelineWithId) => complete(PipelineResponse.fromDomain(pipelineWithId))
              case Left(error) => complete(translateError(error))
            }
          } ~
          delete {
            onSuccess(service.delete(id)) {
              case Right(_) => complete(IdResponse(id))
              case Left(error) => complete(translateError(error))
            }
          }
        }
      }
    }
  }

}

trait PipelineErrorHandling { self: BaseRoutes =>

  protected def translateError(error: PipelineServiceError): (StatusCode, ErrorResponse) = {
    error match {
      case PipelineServiceError.AccessDenied =>
        errorResponse(StatusCodes.Forbidden, "Access denied")
      case PipelineServiceError.PipelineNotFound =>
        errorResponse(StatusCodes.NotFound, "Pipeline not found")
      case PipelineServiceError.PipelineInUse =>
        errorResponse(StatusCodes.NotFound, "Pipeline in use")
      case PipelineServiceError.AssetNotFound =>
        errorResponse(StatusCodes.NotFound, "Asset not found")
      case PipelineServiceError.SortingFieldUnknown =>
        errorResponse(StatusCodes.BadRequest, "Sorting field unknown")
      case PipelineServiceError.NameIsTaken =>
        errorResponse(StatusCodes.BadRequest, "Provided pipeline name is already taken")
      case PipelineServiceError.EmptyPipelineName =>
        errorResponse(StatusCodes.BadRequest, "Pipeline name can not be empty")
      case PipelineServiceError.NameNotSpecified =>
        errorResponse(StatusCodes.BadRequest, "Pipeline name not specified")
      case PipelineServiceError.PipelineStepValidationError(validationError) =>
        translateError(validationError)
      case PipelineServiceError.PipelineOperatorError(pipelineOperatorServiceError) =>
        translateError(pipelineOperatorServiceError)
    }
  }

  protected def translateError(error: PipelineValidationError): (StatusCode, ErrorResponse) = {
    error match {
      case PipelineValidationError.OperatorNotFound(operatorId) =>
        errorResponse(StatusCodes.BadRequest, s"Pipeline operator $operatorId not found")
      case PipelineValidationError.StepsIdsAreNotUnique =>
        errorResponse(StatusCodes.BadRequest, "Provided steps Id's are not unique")
      case PipelineValidationError.StepNotFound(stepId) =>
        errorResponse(StatusCodes.BadRequest, s"Step $stepId not found")
      case PipelineValidationError.IncompatibleInput(actualDataType, providedDataType) =>
        errorResponse(
          StatusCodes.BadRequest,
          s"Provided input $providedDataType is not compatible with required input of type $actualDataType"
        )
      case PipelineValidationError.InvalidOutputReference(operatorId, operatorName, outputIndex) =>
        errorResponse(
          StatusCodes.BadRequest,
          s"Operator $operatorName (#$operatorId) does not have output index $outputIndex"
        )
      case PipelineValidationError.PipelineParamNotFound(paramName) =>
        errorResponse(StatusCodes.BadRequest, s"Provided pipeline parameter $paramName is not an operator parameter")
      case PipelineValidationError.InvalidParamValue(errorMsg) =>
        errorResponse(StatusCodes.BadRequest, s"Invalid value for param: $errorMsg")
      case PipelineValidationError.InvalidInput(operatorId, operatorName, inputName) =>
        errorResponse(StatusCodes.BadRequest, s"Unknown input $inputName for operator $operatorName (#$operatorId)")
      case PipelineValidationError.ParamConditionNotSatisfied(conditionParam, paramName, errorMsg) =>
        errorResponse(
          StatusCodes.BadRequest,
          s"Param $conditionParam is not available since $paramName does not meet condition: $errorMsg"
        )
      case PipelineValidationError.DerivedConditionalParameterMissing(conditionParam, paramName) =>
        errorResponse(
          StatusCodes.BadRequest,
          s"Param $paramName requires all of its dependant parameters values to be set: " +
            s"value for $conditionParam is missing"
        )
      case PipelineValidationError.CircularDependency =>
        errorResponse(StatusCodes.BadRequest, "Pipeline contains circular dependency between steps")
    }
  }

  protected def translateError(error: PipelineOperatorServiceError): (StatusCode, ErrorResponse) = {
    error match {
      case PipelineOperatorServiceError.AccessDenied =>
        errorResponse(StatusCodes.Forbidden, "Access denied")
      case PipelineOperatorServiceError.PipelineOperatorNotFound =>
        errorResponse(StatusCodes.NotFound, "Pipeline operator not found")
      case PipelineOperatorServiceError.SortingFieldUnknown =>
        errorResponse(StatusCodes.BadRequest, "Sorting field unknown")
    }
  }

}
