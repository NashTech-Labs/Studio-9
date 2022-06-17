package baile.routes

import java.io.File

import akka.http.scaladsl.common.StrictForm.Field.FieldUnmarshaller
import akka.http.scaladsl.model.MediaType.NotCompressible
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.ContentDispositionTypes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives.{ mapRejections, mapResponse }
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.unmarshalling.{ FromStringUnmarshaller, Unmarshal, Unmarshaller }
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.routes.contract.common.ErrorResponse
import baile.utils.TryExtensions._
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport.PlayJsonError
import play.api.libs.json._

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

trait BaseRoutes extends PlayJsonSupport {

  // scalastyle:off field.name
  protected val `application/x-directory`: MediaType.Binary =
    MediaType.customBinary("application", "x-directory", NotCompressible)
  // scalastyle:on field.name

  implicit protected val printer: JsValue => String = Json.stringify

  implicit def fieldUnmarshaller[T](implicit fsu: FromStringUnmarshaller[T]): FieldUnmarshaller[T] =
    FieldUnmarshaller.fromBoth(
      fsu,
      Unmarshaller.stringUnmarshaller andThen fsu
    )

  // this should delete uploaded file if nested directive rejects, responds or throws an exception
  protected def tempUploadFile(fileField: String): Directive[(FileInfo, File)] = {
    def prepareTempFile(fileInfo: FileInfo): File = {
      val file = File.createTempFile(fileInfo.fileName, ".tmp")
      file.deleteOnExit()
      file
    }

    storeUploadedFile(fileField, prepareTempFile).tflatMap {
      case (fileInfo: FileInfo, file: File) =>
        def cleanup(): Unit = {
          file.delete()
        }

        val exceptionHandler = ExceptionHandler {
          case e: Exception =>
            cleanup()
            throw e
        }

        val rejectionHandler = (r: immutable.Seq[Rejection]) => {
          cleanup()
          r
        }

        val responseHandler = (response: HttpResponse) => {
          cleanup()
          response
        }

        (handleExceptions(exceptionHandler) & mapRejections(rejectionHandler) & mapResponse(responseHandler))
          .tmap(_ => (fileInfo, file))
    }
  }

  protected def errorResponse(code: StatusCode, error: String): (StatusCode, ErrorResponse) =
    (code, ErrorResponse(code.intValue, error))

  protected def completeWithFile(
    data: Source[ByteString, Any],
    fileName: String,
    contentType: ContentType = ContentTypes.`application/octet-stream`
  ): Route =
    respondWithHeaders(headers.`Content-Disposition`(
      ContentDispositionTypes.attachment,
      Map("filename" -> fileName)
    )) { complete(HttpEntity(contentType, data)) }

  /**
   * String => `A`
   *
   * @tparam A type to decode
   * @return unmarshaller for `A`
   */
  protected def fromStringUnmarshaller[A: Reads]: FromStringUnmarshaller[A] =
    fromStringAsJsValueUnmarshaller(JsString(_))

  /**
   * Json String => `A`
   *
   * @tparam A type to decode
   * @return unmarshaller for `A`
   */
  protected def fromJsonUnmarshaller[A: Reads]: FromStringUnmarshaller[A] =
    fromStringAsJsValueUnmarshaller(Json.parse)

  /**
   * String => JsValue (with given function) => `A`
   *
   * @tparam A type to decode
   * @return unmarshaller for `A`
   */
  protected def fromStringAsJsValueUnmarshaller[A: Reads](toJsValue: String => JsValue): FromStringUnmarshaller[A] =
    new FromStringUnmarshaller[A] {
      override def apply(value: String)(implicit ec: ExecutionContext, materializer: Materializer): Future[A] = Try {
        implicitly[Reads[A]]
          .reads(toJsValue(value))
          .recoverTotal { e =>
            throw RejectionError(
              ValidationRejection(JsError.toJson(e).toString, Some(PlayJsonError(e)))
            )
          }
      }.toFuture
    }

  protected def CsvOptionSeq[A: Reads]: FromStringUnmarshaller[immutable.Seq[Option[A]]] =
    CsvSeq[Option[A]](fromStringUnmarshaller[Option[A]](optionWithEmptyString))

  protected def optionWithEmptyString[A: Reads]: Reads[Option[A]] =
    Reads[Option[A]] {
      case jsString: JsString if jsString.value.nonEmpty => implicitly[Reads[A]].reads(jsString).map(Some(_))
      case _ => JsSuccess(None)
    }

  protected def segment[T](f: PartialFunction[String, T]): PathMatcher1[T] = PathMatchers.Segment.flatMap(f.lift(_))

  protected def contentType(value: ContentType): Directive0 =
    extractRequestEntity.map(_.contentType).flatMap {
      case `value` => pass
      case _ => reject
    }

  protected def headerAs[T: FromStringUnmarshaller](name: String): Directive1[T] =
    for {
      headerValue <- headerValueByName(name)
      ctx <- extractRequestContext
      unmarshalResult <- onComplete(Unmarshal(headerValue).to[T](
        implicitly[FromStringUnmarshaller[T]],
        ctx.executionContext,
        ctx.materializer
      ))
      result <- unmarshalResult match {
        case Success(value) => provide(value)
        case Failure(RejectionError(rejection)) => reject(rejection): Directive1[T]
        case Failure(ex) => throw ex
      }
    } yield result

  protected def pathToString(path: Path): String =
    path match {
      case Path.Empty => ""
      case Path.Slash(tail) => "/" ++ pathToString(tail)
      case Path.Segment(head, tail) => head ++ pathToString(tail)
    }

}

object BaseRoutes {

  def seal(conf: Config)(route: Route): Route = {

    val exceptionHandler = ExceptionHandler {
      case e: Exception =>
        complete(HttpResponse(
          StatusCodes.InternalServerError,
          Nil,
          HttpEntity(
            ContentTypes.`application/json`,
            Json.stringify(Json.toJson(ErrorResponse(
              StatusCodes.InternalServerError.intValue,
              "Internal server error",
              errors = {
                if (conf.getBoolean("routes.debug-exceptions")) Seq(
                  JsObject(Seq(
                    "error" -> JsString(e.getClass.toString),
                    "message" -> JsString(e.getMessage),
                    "stackTrace" -> JsArray(e.getStackTrace.map(line => JsString(line.toString)))
                  ))
                )
                else Seq.empty
              }
            )))
          )
        ))
    }

    val rejectionHandler = RejectionHandler.newBuilder()
      .handle { case ValidationRejection(msg, _) =>
        complete((StatusCodes.BadRequest, "Invalid entry: " + msg))
      }
      .result()
      .withFallback(RejectionHandler.default)
      .mapRejectionResponse {
        // since all Akka default rejection responses are Strict this will handle all other rejections
        case res@HttpResponse(_, _, ent: HttpEntity.Strict, _) =>
          res.copy(entity = HttpEntity(ContentTypes.`application/json`, Json.stringify(Json.toJson(ErrorResponse(
            code = res.status.intValue,
            message = ent.data.utf8String
          )))))
        case x => x // pass through all other types of responses
      }

    (handleExceptions(exceptionHandler) & handleRejections(rejectionHandler))(route)
  }


}
