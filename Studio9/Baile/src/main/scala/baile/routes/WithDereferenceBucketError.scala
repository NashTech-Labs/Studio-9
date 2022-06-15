package baile.routes

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import baile.routes.contract.common.ErrorResponse
import baile.services.common.S3BucketService
import baile.services.common.S3BucketService.BucketDereferenceError

trait WithDereferenceBucketError { _: BaseRoutes =>

  protected def translateError(error: BucketDereferenceError): (StatusCode, ErrorResponse) = error match {
    case S3BucketService.BucketNotFound =>
      errorResponse(StatusCodes.BadRequest, "Bucket was not found")
    case S3BucketService.InvalidAWSRegion =>
      errorResponse(StatusCodes.BadRequest, "AWS region is invalid")
    case S3BucketService.EmptyKey =>
      errorResponse(StatusCodes.BadRequest, "Access or secret key can not be empty")
  }

}
