package baile.routes.dcproject

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import java.time.Instant

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.headers.CacheDirectives.`no-cache`
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.domain.asset.AssetScope
import baile.routes.contract.asset.AssetScopeReads
import baile.domain.dcproject.DCProject
import baile.domain.usermanagement.User
import baile.routes.{ AuthenticatedRoutes, WithAssetProcessRoute }
import baile.routes.contract.common.IdResponse._
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.dcproject.{
  DCProjectCreateRequest,
  DCProjectFileResponse,
  DCProjectResponse,
  DCProjectUpdateRequest
}
import baile.services.common.AuthenticationService
import baile.services.dcproject.DCProjectService
import baile.services.dcproject.DCProjectService.DCProjectServiceError
import com.typesafe.config.Config

import scala.collection.immutable

class DCProjectRoutes(
  val conf: Config,
  val authenticationService: AuthenticationService,
  val service: DCProjectService
) extends AuthenticatedRoutes with WithAssetProcessRoute[DCProject] {

  val routes: Route = authenticated { authParams =>
    implicit val user: User = authParams.user
    pathPrefix("dc-projects") {
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
          val data = service.list(
            scope,
            search,
            order.getOrElse(Seq.empty),
            page.getOrElse(1),
            page_size.getOrElse(conf.getInt("routes.default-page-size")),
            projectId,
            folderId
          )
          onSuccess(data) {
            case Right((list, count)) => complete(ListResponse(
              list.map(DCProjectResponse.fromDomain),
              count
            ))
            case Left(error) => complete(translateError(error))
          }
        } ~
        (post & entity(as[DCProjectCreateRequest])) { projectCreateRequest =>
          onSuccess(service.create(
            projectCreateRequest.name,
            projectCreateRequest.description
          )) {
            case Right(projectWithId) => complete(DCProjectResponse.fromDomain(projectWithId))
            case Left(error) => complete(translateError(error))
          }
        }
      } ~
      pathPrefix(Segment) { id =>
        pathEnd {
          (get & parameters('shared_resource_id.?)) { sharedResourceId =>
            onSuccess(service.get(id, sharedResourceId)) {
              case Right(projectWithId) => complete(DCProjectResponse.fromDomain(projectWithId))
              case Left(error) => complete(translateError(error))
            }
          } ~
          (put & entity(as[DCProjectUpdateRequest])) { projectUpdateRequest =>
            onSuccess(service.update(
              id,
              projectUpdateRequest.name,
              projectUpdateRequest.description
            )) {
              case Right(projectWithId) => complete(DCProjectResponse.fromDomain(projectWithId))
              case Left(error) => complete(translateError(error))
            }
          } ~
          delete {
            onSuccess(service.delete(id)) {
              case Right(_) => complete(IdResponse(id))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        processRoute(id) ~
        path("files" / RemainingPath) { rawFilePath =>
          val filePath = pathToString(rawFilePath)
          put {
            contentType(ContentType(`application/x-directory`)) {
              headerValueByName("x-move-source") { from =>
                onSuccess(service.moveFolder(id, from, filePath)) {
                  case Right(folder) => complete(DCProjectFileResponse.fromDomain(folder))
                  case Left(error) => complete(translateError(error))
                }
              } ~
              onSuccess(service.createFolder(id, filePath)) {
                case Right(folder) => complete(DCProjectFileResponse.fromDomain(folder))
                case Left(error) => complete(translateError(error))
              }
            } ~
            headerValueByName("x-move-source") { from =>
              onSuccess(service.moveFile(id, from, filePath)) {
                case Right(file) => complete(DCProjectFileResponse.fromDomain(file))
                case Left(error) => complete(translateError(error))
              }
            } ~
            headerValueByName("x-copy-source") { from =>
              onSuccess(service.copyFile(id, from, filePath)) {
                case Right(file) => complete(DCProjectFileResponse.fromDomain(file))
                case Left(error) => complete(translateError(error))
              }
            } ~
            extractRequestContext { ctx =>
              import ctx.materializer
              extractRequestEntity { entity =>
                ctx.request.header[headers.`If-Unmodified-Since`] match {
                  case Some(unmodifiedSince) =>
                    onSuccess(service.updateFile(
                      id,
                      filePath,
                      Instant.ofEpochMilli(unmodifiedSince.date.clicks),
                      entity.dataBytes
                    )) {
                      case Right(file) => complete(DCProjectFileResponse.fromDomain(file))
                      case Left(error) => complete(translateError(error))
                    }
                  case None =>
                    onSuccess(service.createFile(
                      id,
                      filePath,
                      entity.dataBytes
                    )) {
                      case Right(file) => complete(DCProjectFileResponse.fromDomain(file))
                      case Left(error) => complete(translateError(error))
                    }
                }
              }
            }
          } ~
          get {
            onSuccess(service.getFile(id, filePath)) {
              case Right(file) =>
                val lastModifiedDateTime = DateTime(file.lastModified.toEpochMilli)
                conditional(lastModified = lastModifiedDateTime) {
                  onSuccess(service.getFileContent(id, filePath)) {
                    case Right(source) =>
                      complete(HttpResponse(
                        headers = immutable.Seq(
                          headers.`Last-Modified`(lastModifiedDateTime),
                          headers.`Cache-Control`(`no-cache`)
                        ),
                        entity = HttpEntity(ContentTypes.`application/octet-stream`, source)
                      ))
                    case Left(error) =>
                      complete(translateError(error))
                  }
                }
              case Left(error) => complete(translateError(error))
            }
          } ~
          delete {
            onSuccess(service.removeObject(id, filePath)) {
              case Right(_) => complete(IdResponse(id))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        path("ls") {
          (get & parameters((
            'path.?,
            'recursive.as[Boolean].?,
            'shared_resource_id.?
          ))) { (path, recursive, sharedResourceId) =>
            onSuccess(service.listFolder(id, path, recursive.getOrElse(false), sharedResourceId)) {
              case Right(list) => complete(list.map(DCProjectFileResponse.fromDomain))
              case Left(error) => complete(translateError(error))
            }
          }
        }
      }
    }
  }

  private def translateError(error: DCProjectServiceError): (StatusCode, ErrorResponse) = {
    error match {
      case DCProjectServiceError.AccessDenied =>
        errorResponse(StatusCodes.Forbidden, "Access denied")
      case DCProjectServiceError.DCProjectNotFound =>
        errorResponse(StatusCodes.NotFound, "Project not found")
      case DCProjectServiceError.AssetNotFound =>
        errorResponse(StatusCodes.NotFound, "Asset not found")
      case DCProjectServiceError.SortingFieldUnknown =>
        errorResponse(StatusCodes.BadRequest, "Sorting field unknown")
      case DCProjectServiceError.NameIsTaken =>
        errorResponse(StatusCodes.BadRequest, "Provided project name is already taken")
      case DCProjectServiceError.NameNotSpecified =>
        errorResponse(StatusCodes.BadRequest, "Project name not specified")
      case DCProjectServiceError.EmptyProjectName =>
        errorResponse(StatusCodes.BadRequest, "Project name can not be empty")
      case DCProjectServiceError.FileWasUpdated =>
        errorResponse(StatusCodes.Conflict, "File was updated since the last time")
      case DCProjectServiceError.ObjectAlreadyExists =>
        errorResponse(StatusCodes.Conflict, "Object already exists")
      case DCProjectServiceError.ObjectNotFound =>
        errorResponse(StatusCodes.NotFound, "Object is not found")
      case DCProjectServiceError.ObjectNotFile =>
        errorResponse(StatusCodes.BadRequest, "Object is not file")
      case DCProjectServiceError.FolderIsNotEmpty =>
        errorResponse(StatusCodes.BadRequest, "Folder is not empty")
      case DCProjectServiceError.ProjectIsNotInIdleMode =>
        errorResponse(StatusCodes.BadRequest, "Project is not in Idle mode")
      case DCProjectServiceError.PathNotFound(path) =>
        errorResponse(StatusCodes.BadRequest, s"Path $path not found")
      case DCProjectServiceError.DCProjectInUse =>
        errorResponse(StatusCodes.BadRequest, "Project in use")
    }
  }

}
