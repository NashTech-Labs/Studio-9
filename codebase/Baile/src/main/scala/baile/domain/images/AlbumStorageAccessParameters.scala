package baile.domain.images

import baile.domain.remotestorage.TemporaryCredentials

case class AlbumStorageAccessParameters(
  credentials: TemporaryCredentials,
  albumBasePath: String
)
