package baile.routes.cv.model

import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.directives.BasicDirectives.extractRequestContext
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.MarshallingDirectives.{ as, entity }
import akka.http.scaladsl.server.{ MissingFormFieldRejection, Route }
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.domain.asset.AssetScope
import baile.domain.cv.model.CVModel
import baile.domain.usermanagement.User
import baile.routes.WithFileUploading.MultipartFileHandlingError.{ PartIsMissing, UploadedFileHandlingFailed }
import baile.routes.contract.asset.AssetScopeReads
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.cv.model.{ CVModelResponse, CVModelSaveRequest, CVModelUpdateRequest }
import baile.routes.{ AuthenticatedRoutes, WithAssetProcessRoute, WithFileUploading }
import baile.services.common.{ AuthenticationService, FileUploadService }
import baile.services.cv.model.CVModelService
import baile.services.cv.model.CVModelService._
import com.typesafe.config.Config
import play.api.libs.json.JsString

import scala.concurrent.ExecutionContext

class CVModelRoutes(
  val conf: Config,
  val authenticationService: AuthenticationService,
  val service: CVModelService,
  val fileUploadService: FileUploadService,
  val appUrl: String
)(implicit ec: ExecutionContext) extends AuthenticatedRoutes
  with WithAssetProcessRoute[CVModel]
  with WithFileUploading {

  val routes: Route = pathPrefix("cv-models") {
    concat(
      pathEnd {
        authenticated { authParams =>
          implicit val user: User = authParams.user
          concat(
            parameters((
              'scope.as[AssetScope](fromStringUnmarshaller[AssetScope]).?,
              'search.?,
              'page.as[Int].?,
              'page_size.as[Int].?,
              'order.as(CsvSeq[String]).?,
              'projectId.?,
              'folderId.?
            )) {
              (scope, search, page, page_size, order, projectId, folderId) =>
                get {
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
                    case Right((list, count)) => complete(ListResponse(list.map(CVModelResponse.fromDomain), count))
                    case Left(error) => complete(translateError(error))
                  }
                }
            }
          )
        }
      },
      path("import") {
        (post & withoutSizeLimit) {
          authenticated { authParams =>
            implicit val user: User = authParams.user
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
                  case Right(model) => complete(CVModelResponse.fromDomain(model))
                }
              }
            }
          }
        }
      },
      pathPrefix(Segment) { cvModelId =>
        path("exportFile") {
          authenticatedWithQueryParam { authParams =>
            withoutRequestTimeout {
              onSuccess(service.export(cvModelId)(authParams.user)) {
                case Right(data) => completeWithFile(data, s"cv-model-$cvModelId.bin")
                case Left(error) => complete(translateError(error))
              }
            }
          }
        } ~
        authenticated { authParams =>
          implicit val user: User = authParams.user
          pathEnd {
            (get & parameters('shared_resource_id.?)) { sharedResourceId =>
              onSuccess(service.get(cvModelId, sharedResourceId)) {
                case Right(model) => complete(CVModelResponse.fromDomain(model))
                case Left(error) => complete(translateError(error))
              }
            } ~
            put {
              entity(as[CVModelUpdateRequest]) { update =>
                onSuccess(service.update(cvModelId, update.name, update.description)) {
                  case Right(model) => complete(CVModelResponse.fromDomain(model))
                  case Left(error) => complete(translateError(error))
                }
              }
            } ~
            delete {
              onSuccess(service.delete(cvModelId)) {
                case Right(_) => complete(IdResponse(cvModelId))
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          path("export") {
            get {
              // Even though this route should only return uri and not make any validations, it is hard for web client
              // to handle ../exportFile error response by showing it the viewable layout. Therefore, we have to return
              // any validation errors in response for this endpoint. That is why here we 'pretend' that we are about to
              // return actual model file and not just signed uri. In the end, we still return only uri, but only when
              // it is expected that next call for this uri leads to a successful model file download
              onSuccess(service.export(cvModelId)) {
                case Right(_) =>
                  val exportFileUri = Uri(s"$appUrl/cv-models/$cvModelId/exportFile").withQuery(
                    Query(tokenQueryParamName -> authParams.token)
                  )
                  complete(JsString(exportFileUri.toString))
                case Left(error) =>
                  complete(translateError(error))
              }
            }
          } ~
          path("state-file-url") {
            get {
              onSuccess(service.getStateFileUrl(cvModelId)) {
                case Right(url) => complete(JsString(url))
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          path("save") {
            (post & entity(as[CVModelSaveRequest])) { modelSaveRequest =>
              onSuccess(service.save(cvModelId, modelSaveRequest.name, modelSaveRequest.description)) {
                case Right(modelWithId) => complete(CVModelResponse.fromDomain(modelWithId))
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          processRoute(cvModelId)
        }
      }
    )
  }

  def translateError(error: CVModelServiceError): (StatusCode, ErrorResponse) = error match {
    case CVModelServiceError.ModelNotFound =>
      errorResponse(StatusCodes.NotFound, "CV model not found")
    case CVModelServiceError.AccessDenied =>
      errorResponse(StatusCodes.Forbidden, "Access denied")
    case CVModelServiceError.SortingFieldUnknown =>
      errorResponse(StatusCodes.BadRequest, "Sorting field not known")
    case CVModelServiceError.CantDeleteRunningModel =>
      errorResponse(StatusCodes.Conflict, "Can't delete running model")
    case CVModelServiceError.ModelNotActive =>
      errorResponse(StatusCodes.BadRequest, "This can be done only to active model")
    case CVModelServiceError.EmptyModelName =>
      errorResponse(StatusCodes.BadRequest, "Model name must not be empty")
    case CVModelServiceError.NameIsTaken =>
      errorResponse(StatusCodes.Conflict, "Provided model name is already taken")
    case CVModelServiceError.CVModelInUse =>
      errorResponse(StatusCodes.BadRequest, "Model in use")
    case CVModelServiceError.CantExportCVModel =>
      errorResponse(StatusCodes.BadRequest, "This model can not be exported")
    case CVModelServiceError.ModelAlreadyInLibrary =>
      errorResponse(StatusCodes.BadRequest, "This model has already been saved in library")
    case CVModelServiceError.ModelFilePathNotFound =>
      errorResponse(StatusCodes.BadRequest, "File path of this model not found")
  }

  def translateError(error: CVModelImportError): (StatusCode, ErrorResponse) = error match {
    case CVModelImportError.InLibraryWrongFormat =>
      errorResponse(StatusCodes.BadRequest, "inLibrary should be boolean")
    case CVModelImportError.EmptyModelName =>
      errorResponse(StatusCodes.BadRequest, "Model name must not be empty")
    case CVModelImportError.NameIsTaken =>
      errorResponse(StatusCodes.Conflict, "Provided model name is already taken")
    case CVModelImportError.NameNotSpecified =>
      errorResponse(StatusCodes.Conflict, "Name not specified")
    case CVModelImportError.ImportedMetaIsTooBig =>
      errorResponse(
        StatusCodes.BadRequest,
        "Provided model file contains too big meta part"
      )
    case CVModelImportError.ImportedMetaFormatError(error) =>
      errorResponse(
        StatusCodes.BadRequest,
        s"Could not parse model meta. Error: $error"
      )
    case CVModelImportError.LocalizationModeNotSpecified =>
      errorResponse(
        StatusCodes.BadRequest,
        "Localization mode was not specified"
      )
    case CVModelImportError.UnsupportedLocalizationMode(mode) =>
      errorResponse(
        StatusCodes.BadRequest,
        s"Localization mode $mode is not supported"
      )
    case CVModelImportError.InvalidCVModelType =>
      errorResponse(
        StatusCodes.BadRequest,
        "CVModel type is invalid"
      )
    case CVModelImportError.PackageNotFound(packageName, packageVersion) =>
      errorResponse(
        StatusCodes.BadRequest,
        packageVersion match {
          case Some(version) => s"Package $packageName of version $version not found"
          case None => s"Package $packageName not found"
        }
      )
    case CVModelImportError.OperatorNotFound(moduleName, className) =>
      errorResponse(
        StatusCodes.BadRequest,
        s"Operator with module name $moduleName and class name $className not found"
      )
  }

}
