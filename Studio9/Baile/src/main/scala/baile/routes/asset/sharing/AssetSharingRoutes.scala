package baile.routes.asset.sharing

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import baile.domain.asset.AssetType
import baile.routes.AuthenticatedRoutes
import baile.routes.contract.asset.SharedResourceRequest._
import baile.routes.contract.asset.{ AssetTypeFormat, SharedResourceRequest, SharedResourceResponse, _ }
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.services.asset.sharing.AssetSharingService
import baile.services.asset.sharing.AssetSharingService.AssetSharingServiceError
import baile.services.asset.sharing.AssetSharingService.AssetSharingServiceError._
import baile.services.common.AuthenticationService
import com.typesafe.config.Config

class AssetSharingRoutes(
  val conf: Config,
  val authenticationService: AuthenticationService,
  val assetSharingService: AssetSharingService
) extends AuthenticatedRoutes {


  val routes: Route = authenticated { authParams =>
    implicit val user = authParams.user

    pathPrefix("shares") {
      concat(
        pathEnd {
          get {
            parameters((
              'asset_id.?,
              'asset_type.as[AssetType](fromStringUnmarshaller[AssetType]).?
            )) { (assetId, assetType) =>
              onSuccess(assetSharingService.list(assetId, assetType)) {
                case Right((list, count)) => complete(ListResponse(list.map(SharedResourceResponse.fromDomain), count))
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          post {
            entity(as[SharedResourceRequest]) { createParams =>
              val data = assetSharingService.create(
                name = createParams.name,
                recipientId = createParams.recipientId,
                recipientEmail = createParams.recipientEmail,
                assetType = createParams.assetType,
                assetId = createParams.assetId
              )
              onSuccess(data) {
                case Right(sharedResourceWithId) => complete(SharedResourceResponse.fromDomain(sharedResourceWithId))
                case Left(error) => complete(translateError(error))
              }
            }
          }
        },
        pathPrefix(Segment) { id =>
          concat(
            pathEnd {
              get {
                onSuccess(assetSharingService.get(id)) {
                  case Right(model) => complete(SharedResourceResponse.fromDomain(model))
                  case Left(error) => complete(translateError(error))
                }
              } ~
              delete {
                onSuccess(assetSharingService.delete(id)) {
                  case Right(_) => complete(IdResponse(id))
                  case Left(error) => complete(translateError(error))
                }
              }
            },
            path("owner") {
              get {
                onSuccess(assetSharingService.getOwner(id)) {
                  case Right(owner) => complete(UserResponse.fromDomain(owner))
                  case Left(error) => complete(translateError(error))
                }
              }
            },
            path("recipient") {
              get {
                onSuccess(assetSharingService.getRecipient(id)) {
                  case Right(recepient) => complete(UserResponse.fromDomain(recepient))
                  case Left(error) => complete(translateError(error))
                }
              }
            }
          )
        }
      )
    } ~
      path("me" / "shares") {
        get {
          val data = assetSharingService.listAll(user.id)
          onSuccess(data)(listOfSharedResource =>
            complete(
              ListResponse(listOfSharedResource.map(SharedResourceResponse.fromDomain), listOfSharedResource.size)
            )
          )
        }
      }
  }

  private def translateError(error: AssetSharingServiceError): (StatusCode, ErrorResponse) = {
    error match {
      case ResourceNotFound => errorResponse(StatusCodes.NotFound, "Shared resource not found")
      case AccessDenied => errorResponse(StatusCodes.Forbidden, "Access denied")
      case RecipientNotFound => errorResponse(StatusCodes.NotFound, "Recipient not found")
      case RecipientIsNotSpecified => errorResponse(StatusCodes.NotFound, "Recipient is not specified")
      case OwnerNotFound => errorResponse(StatusCodes.NotFound, "Resource Owner not found")
      case AlreadyShared => errorResponse(StatusCodes.NotFound, "Resource already shared")
    }
  }

}
