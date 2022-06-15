package baile.routes.pipeline

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.domain.usermanagement.User
import baile.routes.AuthenticatedRoutes
import baile.routes.contract.common.{ ErrorResponse, ListResponse, Version }
import baile.routes.contract.pipeline.operator.PipelineOperatorResponse
import baile.services.common.AuthenticationService
import baile.services.pipeline.PipelineOperatorService
import baile.services.pipeline.PipelineOperatorService.PipelineOperatorServiceError
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

class PipelineOperatorRoutes(
  val conf: Config,
  val authenticationService: AuthenticationService,
  val service: PipelineOperatorService
)(implicit ec: ExecutionContext) extends AuthenticatedRoutes {

  val routes: Route = authenticated { authParams =>
    implicit val user: User = authParams.user
    pathPrefix("pipeline-operators") {
      pathEnd {
        (get & parameters((
          'page.as[Int].?,
          'page_size.as[Int].?,
          'order.as(CsvSeq[String]).?,
          'moduleName.as[String].?,
          'className.as[String].?,
          'packageName.as[String].?,
          'packageVersion.as[Version](fromStringUnmarshaller[Version]).?
        ))) { (page, page_size, order, moduleName, className, packageName, packageVersion) =>

          val data = service.list(
            order.getOrElse(Seq.empty),
            page.getOrElse(1),
            page_size.getOrElse(conf.getInt("routes.default-page-size")),
            moduleName,
            className,
            packageName,
            packageVersion.map(_.toDomain)
          )

          onSuccess(data) {
            case Right((pipelineOperatorsInfo, count)) => complete(ListResponse(
              pipelineOperatorsInfo.map(operatorInfo => PipelineOperatorResponse.fromDomain(
                operatorInfo.operator,
                operatorInfo.dcProjectPackage
              )),
              count
            ))
            case Left(error) => complete(translateError(error))
          }
        }
      } ~
      path(Segment) { id =>
        get {
          onSuccess(service.getPipelineOperator(id)) {
            case Right(pipelineOperatorInfo) => complete(PipelineOperatorResponse.fromDomain(
              pipelineOperatorInfo.operator,
              pipelineOperatorInfo.dcProjectPackage
            ))
            case Left(error) => complete(translateError(error))
          }
        }
      }
    }
  }

  def translateError(error: PipelineOperatorServiceError): (StatusCode, ErrorResponse) = {
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
