package baile.routes.project

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import baile.domain.asset.Asset
import baile.domain.usermanagement.User
import baile.routes.AuthenticatedRoutes
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.project.folder.{ FolderResponse, ProjectFolderCreate }
import baile.routes.contract.project._
import baile.services.asset.AssetService
import baile.services.common.AuthenticationService
import baile.services.cv.model.CVModelService
import baile.services.cv.prediction.CVPredictionService
import baile.services.dataset.DatasetService
import baile.services.dcproject.DCProjectService
import baile.services.experiment.ExperimentService
import baile.services.images.AlbumService
import baile.services.onlinejob.OnlineJobService
import baile.services.pipeline.PipelineService
import baile.services.project.ProjectService
import baile.services.project.ProjectService.{ ProjectServiceCreateError, ProjectServiceError }
import baile.services.table.TableService
import baile.services.tabular.model.TabularModelService
import baile.services.tabular.prediction.TabularPredictionService
import com.typesafe.config.Config

class ProjectRoutes(
  val conf: Config,
  val authenticationService: AuthenticationService,
  val projectService: ProjectService,
  val albumService: AlbumService,
  val cvModelService: CVModelService,
  val cvPredictionService: CVPredictionService,
  val onlineJobService: OnlineJobService,
  val tableService: TableService,
  val dCProjectService: DCProjectService,
  val experimentService: ExperimentService,
  val pipelineService: PipelineService,
  val datasetService: DatasetService,
  val tabularModelService: TabularModelService,
  val tabularPredictionService: TabularPredictionService
) extends AuthenticatedRoutes {

  val routes: Route = authenticated { authParams =>
    implicit val user: User = authParams.user
    pathPrefix("projects") {
      pathEnd {
        get {
          onSuccess(projectService.listAll) { (list, count) =>
            complete(
              ListResponse(list.map(ProjectResponse.fromDomain), count)
            )
          }
        } ~
        (post & entity(as[ProjectCreateOrUpdateRequest])) { projectCreateRequest =>
          onSuccess(
            projectService.create(projectCreateRequest.name)
          ) {
            case Right(projectWithId) => complete(ProjectResponse.fromDomain(projectWithId))
            case Left(error) => complete(translateError(error))
          }
        }
      } ~
      pathPrefix(Segment) { id =>
        pathEnd {
          get {
            onSuccess(projectService.get(id)) {
              case Right(projectWithId) => complete(ExtendedProjectResponse.fromDomain(projectWithId))
              case Left(error) => complete(translateError(error))
            }
          } ~
          (put & entity(as[ProjectCreateOrUpdateRequest])) { projectUpdateRequest =>
            onSuccess(projectService.update(id, projectUpdateRequest.name)) {
              case Right(projectWithId) => complete(ExtendedProjectResponse.fromDomain(projectWithId))
              case Left(error) => complete(translateError(error))
            }
          } ~
          delete {
            onSuccess(projectService.delete(id)) {
              case Right(_) => complete(IdResponse(id))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        pathPrefix("folders") {
          pathEnd {
            (post & entity(as[ProjectFolderCreate])) { projectFolderCreate =>
              onSuccess(
                projectService.createFolder(id, projectFolderCreate.path)
              ) {
                case Right(folderWithId) => complete(FolderResponse.fromDomain(folderWithId))
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          path(Segment) { folderId =>
            pathEnd {
              get {
                onSuccess(projectService.getFolder(id, folderId)) {
                  case Right(folderWithId) => complete(FolderResponse.fromDomain(folderWithId))
                  case Left(error) => complete(translateError(error))
                }
              } ~
              delete {
                onSuccess(projectService.deleteFolder(id, folderId)) {
                  case Right(_) => complete(IdResponse(folderId))
                  case Left(error) => complete(translateError(error))
                }
              }
            }
          }
        } ~
        pathPrefix(segment {
          case "tables" =>
            projectAssetRoutes(id, tableService)
          case "albums" =>
            projectAssetRoutes(id, albumService)
          case "cv-models" =>
            projectAssetRoutes(id, cvModelService)
          case "cv-predictions" =>
            projectAssetRoutes(id, cvPredictionService)
          case "online-jobs" =>
            projectAssetRoutes(id, onlineJobService)
          case "dc-projects" =>
            projectAssetRoutes(id, dCProjectService)
          case "experiments" =>
            projectAssetRoutes(id, experimentService)
          case "pipelines" =>
            projectAssetRoutes(id, pipelineService)
          case "datasets" =>
            projectAssetRoutes(id, datasetService)
          case "models" =>
            projectAssetRoutes(id, tabularModelService)
          case "predictions" =>
            projectAssetRoutes(id, tabularPredictionService)
        })(identity)
      }
    }
  }

  private def projectAssetRoutes[T <: Asset[_]](
    projectId: String,
    assetService: AssetService[T, _]
  )(implicit user: User): Route =
    path(Segment) { assetId =>
      (put & entity(as[ProjectAssetLink])) { projectAssetLink =>
        onSuccess(projectService.addAsset(projectId, projectAssetLink.folderId, assetId, assetService)) {
          case Right(_) => complete(IdResponse(projectId))
          case Left(error) => complete(translateError(error))
        }
      } ~
      delete {
        onSuccess(projectService.deleteAsset(projectId, assetId, assetService)) {
          case Right(_) => complete(IdResponse(projectId))
          case Left(error) => complete(translateError(error))
        }
      }
    }

  private def translateError(error: ProjectServiceError): (StatusCode, ErrorResponse) = {
    error match {
      case ProjectServiceError.AccessDenied =>
        errorResponse(StatusCodes.Forbidden, "Access denied")
      case ProjectServiceError.ProjectNotFound =>
        errorResponse(StatusCodes.NotFound, "Project not found")
      case ProjectServiceError.ProjectNameAlreadyExists(name) =>
        errorResponse(StatusCodes.BadRequest, s"Project with name $name already exists")
      case ProjectServiceError.AssetAlreadyExistsInProject =>
        errorResponse(StatusCodes.BadRequest, "Asset already exists in project")
      case ProjectServiceError.AssetNotFound =>
        errorResponse(StatusCodes.NotFound, "Asset not found")
      case ProjectServiceError.SortingFieldUnknown =>
        errorResponse(StatusCodes.BadRequest, "Sorting field unknown")
      case ProjectServiceError.FolderNotFound =>
        errorResponse(StatusCodes.NotFound, "Folder not found")
      case ProjectServiceError.FolderParentNotExist =>
        errorResponse(StatusCodes.BadRequest, "Path is invalid")
      case ProjectServiceError.FolderPathIsDuplicate =>
        errorResponse(StatusCodes.BadRequest, "Folder with this name already exists")
    }
  }

  private def translateError(error: ProjectServiceCreateError): (StatusCode, ErrorResponse) = {
    error match {
      case ProjectServiceCreateError.ProjectNameAlreadyExists(name) =>
        errorResponse(StatusCodes.BadRequest, s"Project with name $name already exists")
    }
  }

}
