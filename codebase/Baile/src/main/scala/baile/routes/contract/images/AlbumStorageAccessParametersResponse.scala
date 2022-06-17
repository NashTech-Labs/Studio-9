package baile.routes.contract.images

import baile.domain.images.AlbumStorageAccessParameters
import baile.domain.remotestorage.S3TemporaryCredentials
import baile.routes.contract.images.AlbumStorageS3AccessParametersResponse.AlbumStorageS3AccessParametersResponseWrites
import play.api.libs.json.{ JsObject, OWrites }

trait AlbumStorageAccessParametersResponse {
  val albumBasePath: String
}

object AlbumStorageAccessParametersResponse {

  implicit val AlbumStorageAccessParametersResponseWrites: OWrites[AlbumStorageAccessParametersResponse] =
    new OWrites[AlbumStorageAccessParametersResponse] {
      override def writes(response: AlbumStorageAccessParametersResponse): JsObject = {
        response match {
          case data: AlbumStorageS3AccessParametersResponse => AlbumStorageS3AccessParametersResponseWrites.writes(data)
          case _ => throw new IllegalArgumentException(
            s"AlbumStorageAccessParametersResponse serializer can't serialize ${ response.getClass }"
          )
        }
      }
    }

  def fromDomain(params: AlbumStorageAccessParameters): AlbumStorageAccessParametersResponse =
    params.credentials match {
      case s3Credentials: S3TemporaryCredentials => AlbumStorageS3AccessParametersResponse(
        region = s3Credentials.region,
        albumBasePath = params.albumBasePath,
        bucketName = s3Credentials.bucketName,
        accessKey = s3Credentials.accessKey,
        secretKey = s3Credentials.secretKey,
        sessionToken = s3Credentials.sessionToken
      )
      case _ => throw new RuntimeException(
        s"Can not instantiate AlbumStorageAccessParametersResponse from ${ params.getClass }"
      )
    }

}
