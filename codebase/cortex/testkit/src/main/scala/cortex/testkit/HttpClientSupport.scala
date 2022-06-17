package cortex.testkit

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.stream.ActorMaterializer
import akka.testkit.TestKitBase
import cortex.common.service.{ HttpClient, HttpExtensions }
import org.json4s.{ Formats, Serialization }

import scala.concurrent.{ ExecutionContext, Future }

trait HttpClientSupport extends HttpExtensions { self: TestKitBase =>

  // NOTE: do not use Akka extension here so that the Client is not shared
  // between the tested code and the test itself
  val httpClient = new HttpClient {
    override implicit val system: ActorSystem = self.system
    override implicit val executor: ExecutionContext = system.dispatcher
    override implicit val materializer: ActorMaterializer = ActorMaterializer()
    override implicit val log = Logging(system, getClass)
  }

  implicit def materializer = httpClient.getMaterializer

  def post[A <: AnyRef](uri: String, entity: A, credentials: Option[HttpCredentials])(implicit serialization: Serialization, formats: Formats): Future[HttpResponse] = httpClient.post(uri, entity, credentials)

  def get(uri: String, credentials: Option[HttpCredentials] = None): Future[HttpResponse] = httpClient.get(uri, credentials)

  def put[A <: AnyRef](uri: String, entity: A, credentials: Option[HttpCredentials] = None)(implicit serialization: Serialization, formats: Formats): Future[HttpResponse] = httpClient.put(uri, entity, credentials)

  def delete(uri: String, credentials: Option[HttpCredentials] = None): Future[HttpResponse] = httpClient.delete(uri, credentials)
}
