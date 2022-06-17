package baile.routes

import java.util.UUID

import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.Materializer
import akka.stream.scaladsl.{ Broadcast, Sink, Source }
import akka.util.ByteString
import baile.domain.usermanagement.User
import baile.routes.WithFileUploading.MultipartFileHandlingError
import baile.routes.WithFileUploading.MultipartFileHandlingError.{ PartIsMissing, UploadedFileHandlingFailed }
import baile.services.common.FileUploadService
import baile.utils.streams.InputFileSource
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

trait WithFileUploading { _: BaseRoutes =>

  // TODO not all routes need withUploadFile, so this dependency should also be optional
  // TODO probably decouple this trait into two or provide this service as an argument to withUploadFile method
  val fileUploadService: FileUploadService

  def withUploadFile[E, R](
    data: Multipart.FormData,
    filePartName: String,
    handler: (String, FileInfo, Map[String, String]) => Future[Either[E, R]],
    formFieldsValidator: Map[String, String] => Either[MultipartFileHandlingError[E], Unit] = defaultFieldsValidator _
  )(implicit user: User,
    ec: ExecutionContext,
    materializer: Materializer
  ): Future[Either[MultipartFileHandlingError[E], R]] =
    withUploadStream(
      data,
      filePartName,
      (source, fileInfo, formF) => fileUploadService.withUploadedFile(
        source,
        uploadedFilePath => {
          val result = for {
            form <- EitherT.right[E](formF)
            result <- EitherT(handler(uploadedFilePath, fileInfo, form))
          } yield result
          result.value
        },
        Some(user.id)
      )
    )

  def withUploadFiles[R](
    data: Multipart.FormData,
    filePartName: String
  )(
    uploadHandler: Source[InputFileSource, Any] => R
  )(implicit ec: ExecutionContext,
    materializer: Materializer
  ): R = {
    val inputFileSource = data.parts.collect { case bodyPart if bodyPart.name == filePartName =>
      val fileName = bodyPart.filename.getOrElse(s"file_${ UUID.randomUUID() }")
      InputFileSource(fileName, bodyPart.entity.dataBytes)
    }
    uploadHandler(inputFileSource)
  }

  /**
   * !!!!!!!!!!!!!!
   * USER BEWARE
   * !!!!!!!!!!!!!!
   * The result of handler's Future parameter will be available ONLY AFTER handler's Source parameter will be consumed.
   * If you try to read Future value before you materialize the Source (for validation purposes, for example),
   * you will likely get a deadlock.
   *
   * Details:
   * This restriction is caused by the fact that there is no guarantee that form will be read entirely before source
   * (due to form data not specifying order of fields). Because of that you can't do much but treat this Future with
   * extreme care. We intentionally decided not to have handler's form parameter be of type Future[Either[E, T]] (T for
   * validated/parsed form) rather then Future[Map[String, String]] because the former lures one to provide handler
   * that starts from handling Future result value to fail fast on E, which is, again, the deadlock.
   * !!!!!!!!!!!!!!
   */
  def withUploadStream[E, R](
    data: Multipart.FormData,
    filePartName: String,
    handler: (Source[ByteString, Any], FileInfo, Future[Map[String, String]]) => Future[Either[E, R]]
  )(implicit user: User,
    ec: ExecutionContext,
    materializer: Materializer
  ): Future[Either[MultipartFileHandlingError[E], R]] = {

    val (formF, formSink) = Sink.foldAsync[Map[String, String], BodyPart](Map.empty) { (soFar, next) =>
      if (next.name == filePartName || next.filename.isDefined) {
        Future.successful(soFar)
      } else {
        next.entity.toStrict(2.seconds).map { part =>
          soFar.updated(next.name, part.data.utf8String)
        }
      }
    }.preMaterialize

    val (resultF, fileHandlerSink) =
      Sink.foldAsync[Either[MultipartFileHandlingError[E], R], BodyPart](
        PartIsMissing(filePartName).asLeft
      ) { (error, next) =>
        (next.name, next.filename) match {
          case (`filePartName`, Some(filename)) =>
            val fileInfo = FileInfo(filePartName, filename, next.entity.contentType)
            handler(next.entity.dataBytes, fileInfo, formF).map {
              _.leftMap[MultipartFileHandlingError[E]](UploadedFileHandlingFailed(_))
            }
          case _ =>
            Future.successful(error)
        }
      }.preMaterialize

    data.parts.runWith(Sink.combine(formSink, fileHandlerSink)(Broadcast(_)))
    resultF
  }

  def validateFormFieldsPresent(requiredFields: String*)(formFields: Map[String, String]): Either[PartIsMissing, Unit] =
    requiredFields.foldLeft(().asRight[PartIsMissing]) { (soFar, next) =>
      if (formFields.contains(next)) soFar else PartIsMissing(next).asLeft
    }

  private def defaultFieldsValidator(form: Map[String, String]): Either[Nothing, Unit] = ().asRight

}

object WithFileUploading {

  sealed trait MultipartFileHandlingError[+E]

  object MultipartFileHandlingError {

    case class PartIsMissing(partName: String) extends MultipartFileHandlingError[Nothing]

    case class UploadedFileHandlingFailed[E](error: E) extends MultipartFileHandlingError[E]

  }

}
