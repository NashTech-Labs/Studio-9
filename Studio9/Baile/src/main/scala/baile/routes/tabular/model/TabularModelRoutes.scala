package baile.routes.tabular.model

import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ Multipart, StatusCode, StatusCodes, Uri }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.BasicDirectives.extractRequestContext
import akka.http.scaladsl.server.{ MissingFormFieldRejection, Route }
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.MarshallingDirectives.{ as, entity }
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.domain.asset.AssetScope
import baile.domain.tabular.model.TabularModel
import baile.domain.usermanagement.User
import baile.routes.WithFileUploading.MultipartFileHandlingError.{ PartIsMissing, UploadedFileHandlingFailed }
import baile.routes.contract.asset.AssetScopeReads
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.tabular.model.{ ModelUpdateRequest, TabularModelCloneOrSaveRequest, TabularModelResponse }
import baile.routes.{ AuthenticatedRoutes, BaseRoutes, WithAssetProcessRoute, WithFileUploading }
import baile.services.common.{ AuthenticationService, FileUploadService }
import baile.services.tabular.model.TabularModelService
import baile.services.tabular.model.TabularModelService.{ TabularModelImportError, TabularModelServiceError }
import com.typesafe.config.Config
import play.api.libs.json.JsString

import scala.concurrent.ExecutionContext

class TabularModelRoutes(
  val conf: Config,
  val service: TabularModelService,
  val authenticationService: AuthenticationService,
  val fileUploadService: FileUploadService,
  val appUrl: String
)(implicit ec: ExecutionContext) extends BaseRoutes
  with AuthenticatedRoutes
  with WithAssetProcessRoute[TabularModel]
  with WithFileUploading {

  val routes: Route = pathPrefix("models") {

    authenticated { authParams =>
      implicit val user: User = authParams.user

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
            case Right((list, count)) => complete(ListResponse(list.map(TabularModelResponse.fromDomain), count))
            case Left(error) => complete(translateError(error))
          }
        }
      } ~
      path("import") {
        (post & withoutSizeLimit) {
          extractRequestContext { ctx =>
            import ctx.materializer
            entity(as[Multipart.FormData]) { data =>
              onSuccess(withUploadStream(
                data,
                "file",
                (fileSource, _, paramsF) =>
                  service.importModel(
                    fileSource,
                    paramsF
                  )
              )) {
                case Left(PartIsMissing(partName)) => reject(MissingFormFieldRejection(partName))
                case Left(UploadedFileHandlingFailed(error)) => complete(translateError(error))
                case Right(model) => complete(TabularModelResponse.fromDomain(model))
              }
            }
          }
        }
      } ~
      pathPrefix(Segment) { id =>
        path("export") {
          get {
            // Even though this route should only return uri and not make any validations, it is hard for web client
            // to handle ../exportFile error response by showing it the viewable layout. Therefore, we have to return
            // any validation errors in response for this endpoint. That is why here we 'pretend' that we are about to
            // return actual model file and not just signed uri. In the end, we still return only uri, but only when
            // it is expected that next call for this uri leads to a successful model file download
            onSuccess(service.export(id)) {
              case Right(_) =>
                val exportFileUri = Uri(s"$appUrl/cv-models/$id/exportFile").withQuery(
                  Query(tokenQueryParamName -> authParams.token)
                )
                complete(JsString(exportFileUri.toString))
              case Left(error) =>
                complete(translateError(error))
            }
          }
        } ~
        path("save") {
          (post & entity(as[TabularModelCloneOrSaveRequest])) { modelSaveRequest =>
            onSuccess(service.save(id, modelSaveRequest.name, modelSaveRequest.description)) {
              case Right(modelWithId) => complete(TabularModelResponse.fromDomain(modelWithId))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        path("copy") {
          (post & entity(as[TabularModelCloneOrSaveRequest])) { modelCloneRequest =>
            parameter('shared_resource_id.?) { sharedResourceId =>
              onSuccess(service.clone(id, modelCloneRequest.name, modelCloneRequest.description, sharedResourceId)) {
                case Right(modelWithId) => complete(TabularModelResponse.fromDomain(modelWithId))
                case Left(error) => complete(translateError(error))
              }
            }
          }
        } ~
        path("state-file-url") {
          get {
            onSuccess(service.getStateFileUrl(id)) {
              case Right(url) => complete(JsString(url))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        pathEnd {
          (put & entity(as[ModelUpdateRequest])) { req =>
            onSuccess(service.update(id, req.name, req.description)) {
              case Right(model) => complete(TabularModelResponse.fromDomain(model))
              case Left(error) => complete(translateError(error))
            }
          } ~
          (get & parameters('shared_resource_id.?)) { sharedResourceId =>
            onSuccess(service.get(id, sharedResourceId)) {
              case Right(model) => complete(TabularModelResponse.fromDomain(model))
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
        processRoute(id)
      }
    } ~
    path(Segment / "exportFile") { id =>
      authenticatedWithQueryParam { authParams =>
        withoutRequestTimeout {
          onSuccess(service.export(id)(authParams.user)) {
            case Right(data) => completeWithFile(data, s"model-$id.bin")
            case Left(error) => complete(translateError(error))
          }
        }
      }
    }
  }

  def translateError(error: TabularModelServiceError): (StatusCode, ErrorResponse) = error match {
    case TabularModelServiceError.AccessDenied =>
      errorResponse(StatusCodes.Forbidden, "Access denied")
    case TabularModelServiceError.ModelNotFound =>
      errorResponse(StatusCodes.NotFound, "Model not found")
    case TabularModelServiceError.ModelNameIsEmpty =>
      errorResponse(StatusCodes.BadRequest, "Model name is empty")
    case TabularModelServiceError.ModelNameAlreadyExists =>
      errorResponse(StatusCodes.BadRequest, "Model name already exists")
    case TabularModelServiceError.SortingFieldUnknown =>
      errorResponse(StatusCodes.BadRequest, "Sorting field not known")
    case TabularModelServiceError.TabularModelInUse =>
      errorResponse(StatusCodes.BadRequest, "Tabular Model is in use")
    case TabularModelServiceError.EmptyTabularModelName =>
      errorResponse(StatusCodes.BadRequest, "Tabular Model name is empty")
    case TabularModelServiceError.NameIsTaken =>
      errorResponse(StatusCodes.BadRequest, "Tabular Model name is already taken")
    case TabularModelServiceError.ModelFilePathNotFound =>
      errorResponse(StatusCodes.BadRequest, "Tabular model file path not found")
    case TabularModelServiceError.ModelNotActive =>
      errorResponse(StatusCodes.BadRequest, "This can be done only to active model")
    case TabularModelServiceError.CantExportTabularModel =>
      errorResponse(StatusCodes.BadRequest, "Can't export tabular model")
  }

  def translateError(error: TabularModelImportError): (StatusCode, ErrorResponse) = error match {
    case TabularModelImportError.EmptyModelName =>
      errorResponse(StatusCodes.BadRequest, "Model name is empty")
    case TabularModelImportError.NameIsTaken =>
      errorResponse(StatusCodes.BadRequest, "Model name already exists")
    case TabularModelImportError.NameNotSpecified =>
      errorResponse(StatusCodes.BadRequest, "Model name not specified")
    case TabularModelImportError.ImportedMetaIsTooBig =>
      errorResponse(StatusCodes.BadRequest, "Provided model file contains too big meta part")
    case TabularModelImportError.PackageNotFound(packageName, packageVersion) =>
      errorResponse(StatusCodes.BadRequest, s"Package $packageName of version $packageVersion not found")
    case TabularModelImportError.ImportedMetaFormatError(errorMessage) =>
      errorResponse(StatusCodes.BadRequest, s"Could not parse model meta. Error: $errorMessage")
    case TabularModelImportError.InLibraryWrongFormat =>
      errorResponse(StatusCodes.BadRequest, "inLibrary should be boolean")
  }

}
