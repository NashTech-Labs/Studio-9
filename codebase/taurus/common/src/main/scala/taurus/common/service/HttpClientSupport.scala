package taurus.common.service

import akka.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.event.{ Logging, LoggingAdapter }
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ Authorization, HttpCredentials }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import org.json4s.{ Formats, Serialization }
import taurus.common.rest.marshalling.Json4sHttpSupport
import taurus.common.utils.TryExtensions._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

trait HttpClientSupport extends HttpExtensions { support: Service =>

  val httpClient = HttpClient(context.system)

  implicit def materializer = httpClient.getMaterializer

  def post[A <: AnyRef](uri: String, entity: A, credentials: Option[HttpCredentials])(implicit serialization: Serialization, formats: Formats): Future[HttpResponse] = httpClient.post(uri, entity, credentials)

  def get(uri: String, credentials: Option[HttpCredentials] = None): Future[HttpResponse] = httpClient.get(uri, credentials)

  def put[A <: AnyRef](uri: String, entity: A, credentials: Option[HttpCredentials] = None)(implicit serialization: Serialization, formats: Formats): Future[HttpResponse] = httpClient.put(uri, entity, credentials)

  def delete(uri: String, credentials: Option[HttpCredentials] = None): Future[HttpResponse] = httpClient.delete(uri, credentials)

}

object HttpClient extends ExtensionId[HttpClient] with ExtensionIdProvider {
  override def createExtension(extendedSystem: ExtendedActorSystem): HttpClient = new HttpClient {
    override implicit val system: ActorSystem = extendedSystem
    override implicit val executor: ExecutionContext = system.dispatcher
    override implicit val materializer: ActorMaterializer = ActorMaterializer()
    override implicit val log = Logging(system, getClass)
  }

  override def lookup(): ExtensionId[_ <: Extension] = HttpClient
}

trait HttpClient extends Extension with Json4sHttpSupport {

  protected implicit val system: ActorSystem
  protected implicit val executor: ExecutionContext
  protected implicit val materializer: ActorMaterializer
  protected val log: LoggingAdapter

  def post[A <: AnyRef](uri: String, entity: A, credentials: Option[HttpCredentials])(implicit serialization: Serialization, formats: Formats): Future[HttpResponse] = {
    val result: Future[HttpResponse] =
      for {
        uri <- buildUri(uri).toFuture
        entity <- marshal(entity)
        request <- buildRequest(HttpMethods.POST, uri, entity = Some(entity), credentials = credentials).toFuture
        response <- run(request)
      } yield response

    result
  }

  def get(uri: String, credentials: Option[HttpCredentials]): Future[HttpResponse] = {
    val result: Future[HttpResponse] =
      for {
        uri <- buildUri(uri).toFuture
        request <- buildRequest(HttpMethods.GET, uri, credentials = credentials).toFuture
        response <- run(request)
      } yield response

    result
  }

  def put[A <: AnyRef](uri: String, entity: A, credentials: Option[HttpCredentials])(implicit serialization: Serialization, formats: Formats): Future[HttpResponse] = {
    val result: Future[HttpResponse] =
      for {
        uri <- buildUri(uri).toFuture
        entity <- marshal(entity)
        request <- buildRequest(HttpMethods.PUT, uri, entity = Some(entity), credentials).toFuture
        response <- run(request)
      } yield response

    result
  }

  def delete(uri: String, credentials: Option[HttpCredentials]): Future[HttpResponse] = {
    val result: Future[HttpResponse] =
      for {
        uri <- buildUri(uri).toFuture
        request <- buildRequest(HttpMethods.DELETE, uri, credentials = credentials).toFuture
        response <- run(request)
      } yield response

    result
  }

  // Safely builds a Uri from a String
  def buildUri(uri: String): Try[Uri] = Try {
    Uri(uri)
  }

  def buildRequest(method: HttpMethod, uri: Uri, entity: Option[RequestEntity] = None, credentials: Option[HttpCredentials] = None): Try[HttpRequest] = Try {
    val request = HttpRequest(method, uri)

    implicit class ExtendedRequest(request: HttpRequest) {
      def addEntity(): HttpRequest = {
        entity.fold(request)(e => request.withEntity(e))
      }

      def addCredentials(): HttpRequest = {
        credentials.fold(request)(c => request.addHeader(Authorization(c)))
      }
    }

    request
      .addEntity()
      .addCredentials()
  }

  def run(httpRequest: HttpRequest): Future[HttpResponse] = {
    Http().singleRequest(httpRequest) andThen {
      case Success(httpResponse) => log.debug("Successful HTTP call with Request [{}] and Response [{}]", httpRequest, httpResponse)
      case Failure(e)            => log.error("HTTP call with Request [{}] resulted in error [{}]", httpRequest, e)
    }
  }

  def marshal[A <: AnyRef](entity: A)(implicit serialization: Serialization, formats: Formats): Future[RequestEntity] = {
    Marshal(entity).to[MessageEntity]
  }

  def getMaterializer(): ActorMaterializer = materializer

}

trait HttpExtensions {
  implicit class ExtendedFutureHttpResponse(response: Future[HttpResponse]) {
    import taurus.common.rest.marshalling.Json4sHttpSupport._

    def unwrapTo[A: Manifest](implicit executor: ExecutionContext, materializer: ActorMaterializer, serialization: Serialization, formats: Formats): Future[A] = {
      response.flatMap(response => unmarshal(response.entity).recoverWith {
        case e => Future.failed(new Exception(s"Unexpected response entity [$response] caused error [$e]"))
      })
    }

    def unwrapToOption[A: Manifest](implicit executor: ExecutionContext, materializer: ActorMaterializer, serialization: Serialization, formats: Formats): Future[Option[A]] = {
      response.flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _)  => unmarshal(entity).map(Some(_))
        case HttpResponse(StatusCodes.NotFound, _, _, _) => Future.successful(None)
        case other: HttpResponse                         => Future.failed(new Exception(s"Unexpected response [$other]"))
      }
    }

    private def unmarshal[A: Manifest](entity: ResponseEntity)(implicit materializer: ActorMaterializer, serialization: Serialization, formats: Formats): Future[A] = {
      Unmarshal(entity).to[A]
    }
  }

  implicit class ExtendedStringUri(uri: String) {
    def withQuery(query: Map[String, String]): Try[Uri] = Try {
      Uri(uri).withQuery(Query(query))
    }

    def withQuery(query: (String, String)*): Try[Uri] = {
      withQuery(query.toMap)
    }
  }
}

object HttpExtensions extends HttpExtensions
