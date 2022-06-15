package baile.routes.onlineJob

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.domain.asset.AssetScope
import baile.routes.{ AuthenticatedRoutes, WithDereferenceBucketError }
import baile.routes.contract.asset.AssetScopeReads
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.onlinejob._
import baile.services.common.AuthenticationService
import baile.services.onlinejob.OnlineJobService
import baile.services.onlinejob.OnlineJobService.OnlineJobServiceError
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

class OnlineJobRoutes(
  conf: Config,
  val authenticationService: AuthenticationService,
  onlineJobService: OnlineJobService
)(implicit ec: ExecutionContext) extends AuthenticatedRoutes with WithDereferenceBucketError {

  val routes: Route = authenticated {
    authParams =>
      implicit val user = authParams.user
      pathPrefix("online-jobs") {
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
            val data = onlineJobService.list(
              scope,
              search,
              order.getOrElse(Seq.empty),
              page.getOrElse(1),
              page_size.getOrElse(conf.getInt("routes.default-page-size")),
              projectId,
              folderId
            )
            onSuccess(data) {
              case Right((list, count)) => complete(ListResponse(list.map(OnlineJobResponse.fromDomain), count))
              case Left(error) => complete(translateError(error))
            }
          } ~
          post {
            entity(as[OnlineJobCreateRequest]) { createRequest =>
              val options = createRequest.options.toDomain(createRequest.target.id)
              val data = onlineJobService.create(
                createRequest.name,
                createRequest.enabled.getOrElse(true),
                options,
                createRequest.description
              )
              onSuccess(data) {
                case Right(onlineJob) => complete(OnlineJobResponse.fromDomain(onlineJob))
                case Left(error) => complete(translateError(error))
              }
            }
          }
        } ~
        path(Segment) { onlineJobId =>
          (get & parameters('shared_resource_id.?)) { sharedResourceId =>
            onSuccess(onlineJobService.get(onlineJobId, sharedResourceId)) {
              case Right(model) => complete(OnlineJobResponse.fromDomain(model))
              case Left(error) => complete(translateError(error))
            }
          } ~
          (put & entity(as[OnlineJobUpdateRequest])) { update =>
            onSuccess(
              onlineJobService.update(
                onlineJobId,
                update.name,
                update.description,
                update.enabled
              )
            ) {
              case Right(model) => complete(OnlineJobResponse.fromDomain(model))
              case Left(error) => complete(translateError(error))
            }
          } ~
          delete {
            onSuccess(onlineJobService.delete(onlineJobId)) {
              case Right(_) => complete(IdResponse(onlineJobId))
              case Left(error) => complete(translateError(error))
            }
          }
        }
      }
  }

  private def translateError(error: OnlineJobServiceError): (StatusCode, ErrorResponse) = error match {
    case OnlineJobServiceError.OnlineJobNotFound =>
      errorResponse(StatusCodes.NotFound, "Online job not found")
    case OnlineJobServiceError.AccessDenied =>
      errorResponse(StatusCodes.Forbidden, "Access denied")
    case OnlineJobServiceError.SortingFieldUnknown =>
      errorResponse(StatusCodes.BadRequest, "Sorting field not known")
    case OnlineJobServiceError.OnlineJobAlreadyExists =>
      errorResponse(StatusCodes.BadRequest, "Online job already exists")
    case OnlineJobServiceError.ModelNotFound =>
      errorResponse(StatusCodes.BadRequest, "Model not found")
    case OnlineJobServiceError.ModelNotActive =>
      errorResponse(StatusCodes.BadRequest, "Model not active")
    case OnlineJobServiceError.InvalidModelType =>
      errorResponse(StatusCodes.BadRequest, "Model of this type can not be used for online job")
    case OnlineJobServiceError.OnlineJobInUse =>
      errorResponse(StatusCodes.BadRequest, "Online job in use")
    case OnlineJobServiceError.BucketError(error) =>
      translateError(error)
    case OnlineJobServiceError.NameIsTaken =>
      errorResponse(StatusCodes.BadRequest, "Online job with this name already exists")
    case OnlineJobServiceError.EmptyName =>
      errorResponse(StatusCodes.BadRequest, "Online job name cannot be empty")
    case OnlineJobServiceError.NameNotSpecified =>
      errorResponse(StatusCodes.BadRequest, "Online job name not specified")
  }

}
