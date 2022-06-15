package gemini.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{ complete, handleExceptions, handleRejections }
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.FromStringUnmarshaller
import akka.stream.Materializer
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport.PlayJsonError
import gemini.utils.TryExtensions._
import gemini.routes.contract.common.ErrorResponse
import play.api.libs.json._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait BaseRoutes extends PlayJsonSupport {

  protected def errorResponse(code: StatusCode, error: String): (StatusCode, ErrorResponse) =
    (code, ErrorResponse(code.intValue, error))

  /**
    * String => `A`
    *
    * @tparam A type to decode
    * @return unmarshaller for `A`
    */
  protected def fromStringUnmarshaller[A: Reads]: FromStringUnmarshaller[A] =
    new FromStringUnmarshaller[A] {
      override def apply(value: String)(implicit ec: ExecutionContext, materializer: Materializer): Future[A] =
        Try {
          implicitly[Reads[A]].reads(JsString(value)).recoverTotal { e =>
            throw RejectionError(
              ValidationRejection(JsError.toJson(e).toString, Some(PlayJsonError(e)))
            )
          }
        }.toFuture
    }

  protected def optionWithEmptyString[A: Reads]: Reads[Option[A]] =
    Reads[Option[A]] {
      case jsString: JsString if jsString.value.nonEmpty =>
        implicitly[Reads[A]].reads(jsString).map(Some(_))
      case _ =>
        JsSuccess(None)
    }

  protected def segment[T](f: PartialFunction[String, T]): PathMatcher1[T] =
    PathMatchers.Segment.flatMap(f.lift(_))

}

object BaseRoutes {

  def seal(conf: Config)(route: Route): Route = {

    val exceptionHandler = ExceptionHandler {
      case e: Exception =>
        complete(
          HttpResponse(
            StatusCodes.InternalServerError,
            Nil,
            HttpEntity(
              ContentTypes.`application/json`,
              Json.stringify(
                Json.toJson(
                  ErrorResponse(
                    StatusCodes.InternalServerError.intValue,
                    "Internal server error",
                    errors = {
                      if (conf.getBoolean("routes.debug-exceptions"))
                        Seq(
                          JsObject(
                            Seq(
                              "error" -> JsString(e.getClass.toString),
                              "message" -> JsString(e.getMessage),
                              "stackTrace" -> JsArray(
                                e.getStackTrace.map(line => JsString(line.toString))
                              )
                            )
                          )
                        )
                      else Seq.empty
                    }
                  )
                )
              )
            )
          )
        )
    }

    val rejectionHandler = RejectionHandler
      .newBuilder()
      .handle {
        case ValidationRejection(msg, _) =>
          complete((StatusCodes.BadRequest, "Invalid entry: " + msg))
      }
      .result()
      .withFallback(RejectionHandler.default)
      .mapRejectionResponse {
        // since all Akka default rejection responses are Strict this will handle all other rejections
        case res @ HttpResponse(_, _, ent: HttpEntity.Strict, _) =>
          res.copy(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              Json.stringify(
                Json.toJson(
                  ErrorResponse(
                    code = res.status.intValue,
                    message = ent.data.utf8String
                  )
                )
              )
            )
          )
        case x => x // pass through all other types of responses
      }

    (handleExceptions(exceptionHandler) & handleRejections(rejectionHandler))(route)
  }

}
