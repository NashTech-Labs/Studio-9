package baile.routes.dcproject

import java.util.UUID

import baile.routes.AuthenticatedRoutes
import baile.routes.contract.dcproject._
import baile.services.dcproject.DCProjectPackageService
import akka.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  MediaTypes,
  MessageEntity,
  StatusCode,
  StatusCodes
}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.domain.usermanagement.User
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.dcproject.DCProjectPackageResponse
import baile.services.common.AuthenticationService
import baile.services.dcproject.DCProjectPackageService.{
  DCProjectPackageServiceCreateError,
  DCProjectPackageServiceError,
  ExtendedPackageResponse,
  PipelineOperatorPublishParams
}
import com.typesafe.config.Config
import play.api.libs.json.{ JsValue, Writes }

import scala.concurrent.ExecutionContext

class DCProjectPackageRoutes(
  val conf: Config,
  val authenticationService: AuthenticationService,
  val service: DCProjectPackageService
)(implicit ec: ExecutionContext) extends AuthenticatedRoutes {

  val routes: Route = authenticated { authParams =>
    implicit val user: User = authParams.user
    pathPrefix("dc-projects") {
      pathPrefix(Segment) { dcProjectId =>
        path("build") {
          (post & entity(as[DCProjectPackageBuildRequest])) { packageCreateRequest =>
            onSuccess(service.create(
              dcProjectId,
              packageCreateRequest.name,
              packageCreateRequest.version.toDomain,
              packageCreateRequest.description,
              packageCreateRequest.analyzePipelineOperators
            )) {
              case Right(projectWithId) => complete(DCProjectResponse.fromDomain(projectWithId))
              case Left(error) => complete(translateError(error))
            }
          }
        }
      }
    } ~
    pathPrefix("packages") {
      pathEnd {
        (get & parameters((
          'ownerId.as[UUID].?,
          'search.?,
          'dcProjectId.?,
          'page.as[Int].?,
          'page_size.as[Int].?,
          'order.as(CsvSeq[String]).?
        ))) { (ownerId, search, dcProjectId, page, page_size, order) =>
          val data = service.list(
            ownerId = ownerId,
            search = search,
            dcProjectId = dcProjectId,
            orderBy = order.getOrElse(Seq.empty),
            page = page.getOrElse(1),
            pageSize = page_size.getOrElse(conf.getInt("routes.default-page-size"))
          )
          onSuccess(data) {
            case Right((list, count)) =>
              val signedPackages = list.map(service.signPackage)
              complete(ListResponse(
                signedPackages.map(DCProjectPackageResponse.fromDomain),
                count
              ))
            case Left(error) => complete(translateError(error))
          }
        }
      } ~
      pathPrefix(Segment) { id =>
        pathEnd {
          get {
            onSuccess(service.getPackageWithPipelineOperators(id)) {
              case Right(ExtendedPackageResponse(projectPackageWithId, modelPrimitives, operators)) =>
                val signedPackage = service.signPackage(projectPackageWithId)
                complete(ExtendedDCProjectPackageResponse.fromDomain(signedPackage, modelPrimitives, operators))
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
        path("publish") {
          (post & entity(as[DCProjectPackagePublishRequest])) { packagePublishRequest =>
            onSuccess(service.publish(
              id,
              packagePublishRequest.pipelineOperators.map {
                operator => PipelineOperatorPublishParams(operator.id, operator.categoryId)
              }
            )) {
              case Right(ExtendedPackageResponse(projectPackageWithId, primitives, operators)) =>
                val signedPackage = service.signPackage(projectPackageWithId)
                complete(ExtendedDCProjectPackageResponse.fromDomain(signedPackage, primitives, operators))
              case Left(error) => complete(translateError(error))
            }
          }
        }
      }
    }
  } ~
  authenticatedWithBasicCredentials { authParams =>
    implicit val user: User = authParams.user

    pathPrefix("packages-index") {
      import scalatags.Text.all._
      import scalatags.Text.tags2.title
      implicit val errorResponseMarshaller: ToEntityMarshaller[ErrorResponse] = jsonAsHtmlMarshaller[ErrorResponse]

      (pathEndOrSingleSlash | path("index.html")) {
        onSuccess(service.listPackageNames) { packageNames =>
          val htmlPage = "<!DOCTYPE html>" +
            html(
              head(
                title("DC packages index")
              ),
              body(
                packageNames.map { packageName =>
                  a(href := s"/$packageName")(packageName)
                }
              )
            ).toString()
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, htmlPage))
        }
      } ~
      pathPrefix(Segment) { packageName =>
        (pathEndOrSingleSlash | path("index.html")) {
          onSuccess(service.listPackageArtifacts(packageName)) {
            case Right(artifacts) =>
              val htmlPage = "<!DOCTYPE html>" +
                html(
                  head(
                    title(s"Links for $packageName")
                  ),
                  body(
                    artifacts.map { artifact =>
                      a(href := artifact.url)(artifact.filename)
                    }
                  )
                ).toString()

              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, htmlPage))
            case Left(error) => complete(translateError(error))
          }
        }
      }
    }
  }

  private val htmlWrapperMarshaller: Marshaller[String, MessageEntity] = {
    import scalatags.Text.all._
    Marshaller
      .stringMarshaller(MediaTypes.`text/html`)
      .compose { response =>
        "<!DOCTYPE html>" +
          html(body(p(response))).toString()
      }
  }

  private def jsonAsHtmlMarshaller[A](
    implicit writes: Writes[A],
    printer: JsValue => String
  ): ToEntityMarshaller[A] =
    htmlWrapperMarshaller.compose(printer).compose(writes.writes)

  private def translateError(error: DCProjectPackageServiceError): (StatusCode, ErrorResponse) = {
    error match {
      case DCProjectPackageServiceError.AccessDenied =>
        errorResponse(StatusCodes.Forbidden, "Access denied")
      case DCProjectPackageServiceError.SortingFieldUnknown =>
        errorResponse(StatusCodes.BadRequest, "Sorting field unknown")
      case DCProjectPackageServiceError.DCProjectPackageNotFound =>
        errorResponse(StatusCodes.NotFound, "Package not found")
      case DCProjectPackageServiceError.DCProjectPackageInUse =>
        errorResponse(StatusCodes.BadRequest, "At least one of package published operators are in use")
      case DCProjectPackageServiceError.CategoryNotProvided(operators) =>
        errorResponse(StatusCodes.BadRequest, s"Category not provided for operators $operators")
      case DCProjectPackageServiceError.CategoryNotFound(categoryId) =>
        errorResponse(StatusCodes.BadRequest, s"Category not found $categoryId")
      case DCProjectPackageServiceError.CannotDeletePublishedPackage =>
        errorResponse(StatusCodes.BadRequest, "Published package cannot be deleted")
      case DCProjectPackageServiceError.DCProjectPackageAlreadyPublished =>
        errorResponse(StatusCodes.BadRequest, "Package is already published")
    }
  }

  private def translateError(error: DCProjectPackageServiceCreateError): (StatusCode, ErrorResponse) = {
    error match {
      case DCProjectPackageServiceCreateError.EmptyPackageName =>
        errorResponse(StatusCodes.BadRequest, "Provided package name can not be empty")
      case DCProjectPackageServiceCreateError.DCProjectNotFound =>
        errorResponse(StatusCodes.NotFound, "Project not found")
      case DCProjectPackageServiceCreateError.PackageNameIsRequired =>
        errorResponse(StatusCodes.BadRequest, "Package name is required")
      case DCProjectPackageServiceCreateError.PackageNameAlreadyDefined(packageName) =>
        errorResponse(StatusCodes.Conflict, s"Package name $packageName is already defined for the project")
      case DCProjectPackageServiceCreateError.NameIsTaken =>
        errorResponse(StatusCodes.Conflict, "Provided project name is already taken")
      case DCProjectPackageServiceCreateError.DCProjectNotIdle =>
        errorResponse(StatusCodes.BadRequest, "Project is not idle")
      case DCProjectPackageServiceCreateError.VersionNotGreater =>
        errorResponse(StatusCodes.BadRequest, "Provided version is not greater than previous one")
      case DCProjectPackageServiceCreateError.NotNormalizedPackageName =>
        errorResponse(StatusCodes.BadRequest, "Provided package name must be normalized")
      case DCProjectPackageServiceCreateError.InvalidPackageVersion =>
        errorResponse(StatusCodes.BadRequest, "Package version is invalid")
    }
  }

}
