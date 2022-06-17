package baile.routes.contract.dcproject

import java.time.Instant

import baile.services.remotestorage.{ Directory, File, StoredObject }
import play.api.libs.json.{ Json, OWrites }

case class DCProjectFileResponse(`type`: String, name: String, modified: Option[Instant])

object DCProjectFileResponse {

  def fromDomain(storedObject: StoredObject): DCProjectFileResponse = {
    val (fileType, modified) = storedObject match {
      case file: File => ("FILE", Some(file.lastModified))
      case _: Directory => ("DIR", None)
      case unknown => throw new RuntimeException(
        s"Can not build response out of stored object of class: ${ unknown.getClass }"
      )
    }
    DCProjectFileResponse(fileType, storedObject.path, modified)
  }

  implicit val DCProjectFileResponseWrites: OWrites[DCProjectFileResponse] = Json.writes[DCProjectFileResponse]

}
