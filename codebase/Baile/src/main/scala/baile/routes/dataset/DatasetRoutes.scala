package baile.routes.dataset

import java.util.Date

import akka.http.scaladsl.model.ContentType.Binary
import akka.http.scaladsl.model.{ MediaTypes, Multipart, StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.BasicDirectives.extractRequestContext
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.MarshallingDirectives.{ as, entity }
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.domain.asset.AssetScope
import baile.domain.dataset.Dataset
import baile.domain.usermanagement.User
import baile.routes._
import baile.routes.contract.asset.AssetScopeReads
import baile.routes.contract.common.IdResponse._
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.dataset._
import baile.services.common.{ AuthenticationService, FileUploadService }
import baile.services.dataset.DatasetService
import baile.services.dataset.DatasetService.DatasetServiceError
import baile.utils.streams.TarStreaming
import baile.utils.streams.TarStreaming.TarFile
import cats.data.EitherT
import cats.implicits._
import com.typesafe.config.Config
import play.api.libs.json.JsObject

import scala.concurrent.ExecutionContext

class DatasetRoutes(
  val conf: Config,
  val authenticationService: AuthenticationService,
  val fileUploadService: FileUploadService,
  val service: DatasetService
)(
  implicit ec: ExecutionContext
) extends AuthenticatedRoutes
  with WithAssetProcessRoute[Dataset]
  with WithDereferenceBucketError
  with WithFileUploading {

  val routes: Route =
    authenticated { authParams =>
      implicit val user: User = authParams.user
      pathPrefix("datasets") {
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
              case Right((list, count)) => complete(ListResponse(list.map(DatasetResponse.fromDomain), count))
              case Left(error) => complete(translateError(error))
            }
          } ~
          (post & entity(as[DatasetCreateRequest])) { datasetCreateRequest =>
            onSuccess(service.create(
              datasetCreateRequest.name,
              datasetCreateRequest.description
            )) {
              case Right(datasetWithId) => complete(DatasetResponse.fromDomain(datasetWithId))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        pathPrefix(Segment) { id =>
          path("files") {
            (post & withoutSizeLimit) {
              extractRequestContext { ctx =>
                import ctx.materializer
                entity(as[Multipart.FormData]) { data =>
                  onSuccess(withUploadFiles(
                    data = data,
                    filePartName = "file"
                  )(fileSource => service.upload(id, fileSource))) {
                    case Left(error) => complete(translateError(error))
                    case Right(_) => complete(IdResponse(id))
                  }
                }
              }
            }
          } ~
          path("files" / RemainingPath) { rawFileName =>
            val datasetFileName = pathToString(rawFileName)
            delete {
              onSuccess(service.removeFile(id, datasetFileName)) {
                case Right(_) => complete(IdResponse(id))
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          path("import") {
            (post & entity(as[ImportDatasetFromS3Request])) { request =>
              onSuccess(service.importDatasetFromS3(
                id,
                request.from.s3Bucket.toDomain,
                request.from.path
              )) {
                case Right(dataset) => complete(StatusCodes.Accepted -> DatasetResponse.fromDomain(dataset))
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          path("export") {
            (post & entity(as[ExportDatasetToS3Request])) { request =>
              onSuccess(service.exportDatasetToS3(
                id,
                request.to.s3Bucket.toDomain,
                request.to.path
              )) {
                case Right(_) => complete(StatusCodes.Accepted -> JsObject.empty)
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          path("ls") {
            (get & parameters((
              'page.as[Int].?,
              'page_size.as[Int].?,
              'order.as(CsvSeq[String]).?,
              'shared_resource_id.?,
              'search.?
            ))) { (page, page_size, order, sharedResourceId, search) =>
              val data = for {
                filesWithCount <- EitherT(service.listFiles(
                  id,
                  page.getOrElse(1),
                  page_size.getOrElse(conf.getInt("routes.default-page-size")),
                  order.getOrElse(Seq.empty),
                  sharedResourceId,
                  search
                ))
                (files, count) = filesWithCount
                urls <- EitherT(service.signFiles(id, files, sharedResourceId))
              } yield (files.zip(urls), count)

              onSuccess(data.value) {
                case Right((filesAndUrls, count)) => complete(ListResponse(
                  filesAndUrls.map { case (file, url) => DatasetFileResponse.fromDomain(file, url) },
                  count
                ))
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          processRoute(id) ~
          pathEnd {
            (get & parameters('shared_resource_id.?)) { sharedResourceId =>
              onSuccess(service.get(id, sharedResourceId)) {
                case Right(projectWithId) => complete(DatasetResponse.fromDomain(projectWithId))
                case Left(error) => complete(translateError(error))
              }
            } ~
            (put & entity(as[DatasetUpdateRequest])) { datasetUpdateRequest =>
              onSuccess(service.update(
                id,
                datasetUpdateRequest.name,
                datasetUpdateRequest.description
              )) {
                case Right(projectWithId) => complete(DatasetResponse.fromDomain(projectWithId))
                case Left(error) => complete(translateError(error))
              }
            } ~
            delete {
              onSuccess(service.delete(id)) {
                case Right(_) => complete(IdResponse(id))
                case Left(error) => complete(translateError(error))
              }
            }
          }
        }
      }
    } ~
    authenticatedWithQueryParam { authParams =>
      implicit val user: User = authParams.user
      (path("datasets" / Segment / "download") & parameters('shared_resource_id.?)) { (id, sharedResourceId)  =>
        get {
          downloadTar(id, List.empty, sharedResourceId)
        } ~
        post {
          formField('files.as[Seq[String]](fromJsonUnmarshaller[Seq[String]])) { fileNames =>
            downloadTar(id, fileNames.toList, sharedResourceId)
          } ~
          entity(as[List[String]]) { fileNames =>
            downloadTar(id, fileNames, sharedResourceId)
          }
        }
      }
    }

  private def translateError(error: DatasetServiceError): (StatusCode, ErrorResponse) =
    error match {
      case DatasetServiceError.AccessDenied =>
        errorResponse(StatusCodes.Forbidden, "Access denied")
      case DatasetServiceError.DatasetNotFound =>
        errorResponse(StatusCodes.NotFound, "Dataset not found")
      case DatasetServiceError.DatasetInUse =>
        errorResponse(StatusCodes.NotFound, "Dataset in use")
      case DatasetServiceError.SortingFieldUnknown =>
        errorResponse(StatusCodes.BadRequest, "Sorting field unknown")
      case DatasetServiceError.NameIsTaken =>
        errorResponse(StatusCodes.BadRequest, "Provided dataset name is already taken")
      case DatasetServiceError.NameNotSpecified =>
        errorResponse(StatusCodes.BadRequest, "Dataset name not specified")
      case DatasetServiceError.EmptyDatasetName =>
        errorResponse(StatusCodes.BadRequest, "Dataset name can not be empty")
      case DatasetServiceError.DatasetIsNotActive =>
        errorResponse(StatusCodes.BadRequest, "Dataset is not active")
      case DatasetServiceError.FileNotFound =>
        errorResponse(StatusCodes.NotFound, "File not found")
      case DatasetServiceError.BucketError(bucketError) =>
        translateError(bucketError)
    }

  private def downloadTar(
    id: String,
    fileNames: List[String],
    sharedResourceId: Option[String]
  )(implicit user: User): Route = {
    onSuccess(service.download(id, fileNames, sharedResourceId)) {
      case Right(source) =>
        val tarFilesSource = source.map { streamedFile =>
          TarFile(
            name = streamedFile.file.path,
            size = streamedFile.file.size,
            modificationTime = Date.from(streamedFile.file.lastModified),
            content = streamedFile.content
          )
        }
        completeWithFile(tarFilesSource.via(TarStreaming.TarFlow), s"$id.tar", Binary(MediaTypes.`application/x-tar`))
      case Left(error) =>
        complete(translateError(error))
    }
  }

}
