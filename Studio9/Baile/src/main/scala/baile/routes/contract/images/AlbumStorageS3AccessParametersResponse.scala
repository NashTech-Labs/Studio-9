package baile.routes.contract.images

import play.api.libs.json.{ Json, OWrites }

case class AlbumStorageS3AccessParametersResponse(
  region: String,
  albumBasePath: String,
  bucketName: String,
  accessKey: String,
  secretKey: String,
  sessionToken: String
) extends AlbumStorageAccessParametersResponse

object AlbumStorageS3AccessParametersResponse {

  implicit val AlbumStorageS3AccessParametersResponseWrites: OWrites[AlbumStorageS3AccessParametersResponse] =
    Json.writes[AlbumStorageS3AccessParametersResponse]

}
