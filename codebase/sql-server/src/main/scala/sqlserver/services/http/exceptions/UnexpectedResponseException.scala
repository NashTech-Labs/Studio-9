package sqlserver.services.http.exceptions

import akka.http.scaladsl.model.{ HttpEntity, HttpRequest, StatusCode }

case class UnexpectedResponseException(
  request: HttpRequest,
  actualCode: StatusCode,
  entity: HttpEntity.Strict
) extends RuntimeException(
      s"Unexpected response code for request [$request]: [$actualCode]. Response entity: ${entity.getData.utf8String}"
    )
