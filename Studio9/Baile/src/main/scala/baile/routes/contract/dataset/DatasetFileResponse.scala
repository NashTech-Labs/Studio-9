package baile.routes.contract.dataset

import java.time.Instant

import baile.services.remotestorage.File
import play.api.libs.json.{ Json, OWrites }

/**
 * Entity represents a binary file from dataset
 * @param filename an absolute path for a user and a relative path for baile
 * @param filepath a temporary url to download a file
 * @param filesize a size of a file
 * @param modified time a file was last modified
 */
case class DatasetFileResponse(
  filename: String,
  filepath: String,
  filesize: Long,
  modified: Instant
)

object DatasetFileResponse {

  implicit val DatasetFileWrites: OWrites[DatasetFileResponse] = Json.writes[DatasetFileResponse]

  def fromDomain(file: File, url: String): DatasetFileResponse =
    DatasetFileResponse(
      filename = file.path,
      filepath = url,
      filesize = file.size,
      modified = file.lastModified
    )
}
