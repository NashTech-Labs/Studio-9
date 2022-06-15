package baile.routes.table

import akka.http.scaladsl.model.{ ContentTypes, Multipart, StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.BasicDirectives.extractRequestContext
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.MarshallingDirectives.{ as, entity }
import akka.http.scaladsl.server.{ MissingFormFieldRejection, Route }
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import akka.stream.alpakka.csv.scaladsl.CsvFormatting
import akka.stream.scaladsl.Source
import baile.daocommons.WithId
import baile.domain.asset.AssetScope
import baile.domain.table.{ FileType, Table, TableRow, TableRowValue }
import baile.domain.usermanagement.User
import baile.routes.WithFileUploading.MultipartFileHandlingError.{ PartIsMissing, UploadedFileHandlingFailed }
import baile.routes.contract.asset.AssetScopeReads
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.table._
import baile.routes.{ AuthenticatedRoutes, WithAssetProcessRoute, WithFileUploading }
import baile.services.common.{ AuthenticationService, FileUploadService }
import baile.services.table.TableService
import baile.services.table.TableService.TableServiceError
import com.typesafe.config.Config
import baile.routes.process.ProcessRoutes._
import baile.routes.table.TableRoutes.TableUploadError
import baile.routes.table.TableRoutes.TableUploadError.{ ContractError, ServiceError }
import cats.data.EitherT
import cats.implicits._
import play.api.libs.json.{ JsError, Json }

import scala.concurrent.Future
import scala.util.Try

class TableRoutes(
  val conf: Config,
  val authenticationService: AuthenticationService,
  val service: TableService,
  val fileUploadService: FileUploadService
) extends AuthenticatedRoutes with WithAssetProcessRoute[Table] with WithFileUploading {

  val routes: Route = authenticated { authParams =>
    implicit val user: User = authParams.user

    pathPrefix("tables") {
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

          val data: Future[Either[TableServiceError, (Seq[WithId[Table]], Int)]] = service.list(
            scope,
            search,
            order.getOrElse(Seq.empty),
            page.getOrElse(1),
            page_size.getOrElse(conf.getInt("routes.default-page-size")),
            projectId,
            folderId
          )
          onSuccess(data) {
            case Right((list, count)) => complete(ListResponse(list.map(TableResponse.fromDomain), count))
            case Left(error) => complete(translateError(error))
          }
        }
      } ~
      pathPrefix(Segment) { id =>
        pathEnd {
          (get & parameters('shared_resource_id.?)) { sharedResourceId =>
            onSuccess(service.get(id, sharedResourceId)) {
              case Right(tableWithId) => complete(ExtendedTableResponse.fromDomain(tableWithId))
              case Left(error) => complete(translateError(error))
            }
          } ~
          (put & entity(as[TableUpdateRequest])) { tableUpdateRequest =>
            val data = service.updateTable(
              tableId = id,
              newName = tableUpdateRequest.name,
              updateColumnsParams = tableUpdateRequest.columns.map { updateColumnRequest =>
                TableService.UpdateColumnParams(
                  name = updateColumnRequest.name,
                  align = updateColumnRequest.align,
                  displayName = updateColumnRequest.displayName,
                  variableType = updateColumnRequest.variableType
                )
              },
              newDescription = tableUpdateRequest.description
            )
            onSuccess(data) {
              case Right(tableWithId) => complete(ExtendedTableResponse.fromDomain(tableWithId))
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
        path("copy") {
          (post & entity(as[TableCloneOrSaveRequest])) { tableCloneRequest =>
            parameter('shared_resource_id.?) { sharedResourceId =>
              onSuccess(service.cloneTable(tableCloneRequest.name, id, sharedResourceId)) {
                case Right(tableWithId) => complete(TableResponse.fromDomain(tableWithId))
                case Left(error) => complete(translateError(error))
              }
            }
          }
        } ~
        path("save") {
          (post & entity(as[TableCloneOrSaveRequest])) { tableSaveRequest =>
            onSuccess(service.save(id, tableSaveRequest.name)) {
              case Right(tableWithId) => complete(TableResponse.fromDomain(tableWithId))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        path("values") {
          (get & parameters((
            'column_name,
            'search.?,
            'limit.as[Int].?,
            'shared_resource_id.as[String].?
          ))) { (columnName, search, limit, sharedResourceId) =>
            onSuccess(service.getColumnValues(id, columnName, search, limit.getOrElse(30), sharedResourceId)) {
              case Right(values) => complete(ListResponse(values.map(ListDatasetValueResponse.fromDomain), values.size))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        path("data") {
          (get & parameters((
            'page.as[Int].?,
            'page_size.as[Int].?,
            'order.?,
            'search.?,
            'shared_resource_id.as[String].?
          ))) { (page, pageSize, order, search, sharedResourceId) =>

            val data: Future[Either[TableServiceError, (Seq[TableRow], Long)]] = service.getTableData(
              id,
              pageSize.getOrElse(conf.getInt("routes.default-page-size")),
              page.getOrElse(1),
              search,
              order,
              sharedResourceId
            )

            onSuccess(data) {
              case Right((list, count)) =>
                complete(
                ListResponse(list.map(ListDatasetRowResponse.fromDomain), count)
              )
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        pathPrefix("stats") {
          pathEnd {
            (get & parameter('shared_resource_id.as[String].?)) { sharedResourceId =>
              onSuccess(service.getStatistics(id, sharedResourceId)) {
                case Right(statistics) => complete(TableStatisticsResponse.fromDomain(statistics))
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          path("process") {
            get {
              onSuccess(service.getTableStatisticsProcess(id)) {
                case Right(process) => complete(buildProcessResponse(process,conf))
                case Left(error) => complete(translateError(error))
              }
            }
          }
        } ~
        processRoute(id)
      } ~
      path("import" / segment[FileType] {
        case "json" => FileType.JSON
        case "csv" => FileType.CSV
      }) { fileType =>
        (post & withoutSizeLimit) {
          extractRequestContext { ctx =>
            import ctx.{ executionContext, materializer }

            def parseColumns(columns: Option[String]): Either[TableUploadError, Option[Seq[TableService.ColumnInfo]]] =
              columns match {
                case Some(cols) =>
                  for {
                    json <- Try(Json.parse(cols)).toEither.leftMap { _ =>
                      ContractError("Expected json array for columns info")
                    }
                    request <- Json.fromJson[Seq[ColumnInfoRequest]](json).asEither.leftMap { errors =>
                      ContractError(JsError.toJson(errors).toString)
                    }
                    columnsInfo = request.map { columnInfo =>
                      TableService.ColumnInfo(
                        columnInfo.name,
                        columnInfo.displayName,
                        columnInfo.variableType,
                        columnInfo.dataType
                      )
                    }
                  } yield Some(columnsInfo)
                case None =>
                  None.asRight
              }

            entity(as[Multipart.FormData]) { data =>
              onSuccess(withUploadFile[TableUploadError, WithId[Table]](
                data,
                "file",
                (filePath, _, params) => {
                  val result = for {
                    columns <- EitherT.fromEither[Future](parseColumns(params.get("columns")))
                    serviceResult <- EitherT(service.uploadTable(
                      params = params,
                      fileType = fileType,
                      tableFilePath = filePath,
                      delimiter = params.getOrElse("delimiter", ","),
                      nullValue = params.get("nullValue"),
                      description = params.get("description"),
                      columns = columns
                    )).leftMap[TableUploadError](ServiceError(_))
                  } yield serviceResult
                  result.value
                }
              )) {
                case Left(PartIsMissing(partName)) =>
                  reject(MissingFormFieldRejection(partName))
                case Left(UploadedFileHandlingFailed(ContractError(message))) =>
                  complete(errorResponse(StatusCodes.BadRequest, message))
                case Left(UploadedFileHandlingFailed(ServiceError(error))) =>
                  complete(translateError(error))
                case Right(table) =>
                  complete(TableResponse.fromDomain(table))
              }
            }
          }
        }
      }
    }
  } ~
  authenticatedWithQueryParam { authParams =>
    implicit val user: User = authParams.user
    pathPrefix("tables" / Segment) { tableId =>
      path("export") {
        (get & parameters('shared_resource_id.?)) { sharedResourceId =>
          onSuccess(service.export(tableId, sharedResourceId)) {
            case Right(result) =>

              def tableRowValueToString(tableRowValue: TableRowValue): String = tableRowValue match {
                case TableRowValue.StringValue(value) => value
                case TableRowValue.BooleanValue(value) => value.toString
                case TableRowValue.IntegerValue(value) => value.toString
                case TableRowValue.DoubleValue(value) => value.toString
                case TableRowValue.LongValue(value) => value.toString
                case TableRowValue.TimestampValue(value) => value
                case TableRowValue.NullValue => "null"
              }

              val columnNames = result.table.entity.columns.map(_.name).toList
              val source = result.data
                .map { row =>
                  row.values.map(tableRowValueToString).toList
                }
                .prepend(Source.single(columnNames))
                .via(CsvFormatting.format())

              completeWithFile(source, result.table.entity.name + ".csv", ContentTypes.`text/csv(UTF-8)`)
            case Left(error) => complete(translateError(error))
          }
        }
      }
    }
  }

  def translateError(error: TableServiceError): (StatusCode, ErrorResponse) = error match {
    case TableServiceError.TableNameIsNotUnique(name) =>
      errorResponse(StatusCodes.BadRequest, s"Table with name $name already exists")
    case TableServiceError.TableIsNotActive =>
      errorResponse(StatusCodes.BadRequest, "Table is not active")
    case TableServiceError.TableNameIsEmpty =>
      errorResponse(StatusCodes.BadRequest, "Table name is empty")
    case TableServiceError.NameNotSpecified =>
      errorResponse(StatusCodes.BadRequest, "Table name not specified")
    case TableServiceError.TableNotFound =>
      errorResponse(StatusCodes.NotFound, "Table not found")
    case TableServiceError.AccessDenied =>
      errorResponse(StatusCodes.Forbidden, "Access denied")
    case TableServiceError.SortingFieldUnknown =>
      errorResponse(StatusCodes.BadRequest, "Sorting field not known")
    case TableServiceError.TableStatsProcessNotFound =>
      errorResponse(StatusCodes.BadRequest, "Table statistics process not found")
    case TableServiceError.InvalidContinuousDataType =>
      errorResponse(
        StatusCodes.BadRequest,
        "Continuous Variable is available only for Integer, Long and Double"
      )
    case TableServiceError.TableDoesNotHaveSuchColumn =>
      errorResponse(
        StatusCodes.BadRequest,
        "Table does not have provided column to update"
      )
    case TableServiceError.ColumnNotFound(column: String) =>
      errorResponse(StatusCodes.BadRequest, s"Column { $column } not found")
    case TableServiceError.EmptySortingKey =>
      errorResponse(StatusCodes.BadRequest, "Empty sort parameter provided")
    case TableServiceError.InvalidSearchParams =>
      errorResponse(StatusCodes.BadRequest, "Invalid search parameter provided")
    case TableServiceError.TableInUse =>
      errorResponse(StatusCodes.BadRequest, "Table is in use")
    case TableServiceError.CantUpdateTable =>
      errorResponse(
        StatusCodes.BadRequest,
        "Table cannot be modified because it is already in use another Asset." +
          "Please Clone this Table and make modifications to the Cloned Table"
      )
    case TableServiceError.InLibraryWrongFormat =>
      errorResponse(StatusCodes.BadRequest, "inLibrary should be boolean")
    case TableServiceError.TableColumnNamesNotUnique =>
      errorResponse(StatusCodes.BadRequest, "Column names should be unique")
  }

}

object TableRoutes {

  sealed trait TableUploadError
  object TableUploadError {
    case class ContractError(message: String) extends TableUploadError
    case class ServiceError(error: TableServiceError) extends TableUploadError
  }

}
