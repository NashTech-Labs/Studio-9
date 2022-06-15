package baile.routes.process

import java.time.Instant

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Route, StandardRoute }
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.daocommons.WithId
import baile.domain.process.Process
import baile.domain.usermanagement.User
import baile.routes.AuthenticatedRoutes
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.process.{ JobType, _ }
import baile.services.process.ProcessService
import baile.services.process.ProcessService._
import com.typesafe.config.Config
import play.api.libs.json.Reads
import baile.routes.process.ProcessRoutes._
import baile.services.common.AuthenticationService

class ProcessRoutes(
  conf: Config,
  processService: ProcessService,
  val authenticationService: AuthenticationService
) extends AuthenticatedRoutes {

  val routes: Route = authenticated { authParams =>
    implicit val user: User = authParams.user

    pathPrefix("processes") {
      pathEnd {
        (get & parameters((
          'page.as[Int].?,
          'page_size.as[Int].?,
          'order.as(CsvSeq[String]).?,
          'jobTypes.as(CsvSeq[JobType](fromStringUnmarshaller[JobType])).?,
          'processStarted.as(CsvSeq[Instant](fromStringUnmarshaller(Reads.DefaultInstantReads))).?,
          'processCompleted.as(CsvSeq[Instant](fromStringUnmarshaller(Reads.DefaultInstantReads))).?
        ))) { (page, page_size, order, jobTypes, processStarted, processCompleted) =>

          def list(handlerClasses: Option[Seq[String]]): Route = {
            onSuccess(processService.list(
              orderBy = order.getOrElse(Seq.empty),
              page = page.getOrElse(1),
              pageSize = page_size.getOrElse(conf.getInt("routes.default-page-size")),
              handlerClasses = handlerClasses,
              processStarted = processStarted,
              processCompleted = processCompleted
            )) { result =>
              processErrorOr(result) { case (list, count) =>
                ListResponse(
                  list.map(process => buildProcessResponse(process,conf)),
                  count
                )
              }
            }
          }

          jobTypes match {
            case Some(types) =>
              val unknownJobTypes = types filterNot JobTypeToHandlerClassMap.keySet.contains
              if (unknownJobTypes.nonEmpty) {
                complete(
                  errorResponse(
                    StatusCodes.BadRequest,
                    s"Unknown process job types: ${ unknownJobTypes.mkString(",") }"
                  )
                )
              } else {
                list(Some(types.map(JobTypeToHandlerClassMap(_))))
              }
            case None =>
              list(None)
          }
        }
      } ~
      pathPrefix(Segment) { id =>
        pathEnd {
          get {
            onSuccess(processService.getProcess(id)) { result =>
              processErrorOr(result)(process => buildProcessResponse(process, conf))
            }
          }
        } ~
        path("cancel") {
          post {
            onSuccess(processService.cancelProcess(id)) { result =>
              processErrorOr(result)(_ => IdResponse(id))
            }
          }
        }
      }
    }
  }

  private def processErrorOr[R, T]
    (result: Either[ProcessServiceError, R])
    (f: R => T)
    (implicit ev: ToEntityMarshaller[T]): StandardRoute =
    result match {
      case Left(error: ProcessServiceError) =>
        complete(translateError(error))
      case Right(r) =>
        complete(f(r))
    }

  private def translateError(error: ProcessServiceError): (StatusCode, ErrorResponse) = error match {
    case ProcessNotFoundError => errorResponse(StatusCodes.NotFound, "Process not found")
    case ActionForbiddenError => errorResponse(StatusCodes.Forbidden, "Access denied")
    case SortingFieldUnknown => errorResponse(StatusCodes.BadRequest, "Sorting field not known")
    case InvalidRangeProvided(message) => errorResponse(
      StatusCodes.BadRequest,
      message
    )
  }

}

object ProcessRoutes {

  def buildProcessResponse(process: WithId[Process], conf: Config): ProcessResponse =
    ProcessResponse.fromDomain(
      process,
      conf.getBoolean("routes.debug-exceptions"),
      HandlerClassToJobTypeMap(process.entity.onComplete.handlerClassName)
    )

}
