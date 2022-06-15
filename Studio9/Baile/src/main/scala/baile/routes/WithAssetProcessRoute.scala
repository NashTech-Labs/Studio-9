package baile.routes

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import baile.domain.asset.Asset
import baile.domain.usermanagement.User
import baile.routes.contract.common.ErrorResponse
import baile.services.asset.AssetService.{ AssetProcessGetError, WithProcess }
import com.typesafe.config.Config
import baile.routes.process.ProcessRoutes._

trait WithAssetProcessRoute[T <: Asset[_]] { self: BaseRoutes =>

  val service: WithProcess[T, _]

  val conf: Config

  def processRoute(targetId: String)(implicit user: User): Route =
    path("process") {
      get {
        onSuccess(service.getCurrentProcess(targetId)) {
          case Right(process) =>
            complete(buildProcessResponse(process,conf))
          case Left(error) =>
            complete(translateError(error))
        }
      }
    }

  def translateError(error: AssetProcessGetError): (StatusCode, ErrorResponse) = error match {
    case AssetProcessGetError.AssetNotFound =>
      self.errorResponse(StatusCodes.BadRequest, "Process target asset was not found")
    case AssetProcessGetError.ProcessNotFound =>
      self.errorResponse(StatusCodes.NotFound, "Process was not found")
    case AssetProcessGetError.AccessDenied =>
      self.errorResponse(StatusCodes.Forbidden, "Access to target asset denied")
  }

}
