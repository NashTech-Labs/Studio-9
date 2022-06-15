package sqlserver

import akka.actor.Scheduler
import akka.dispatch.MessageDispatcher
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.{ CallingThreadDispatcher, ImplicitSender, TestKit, TestKitBase }
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory }
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }
import org.scalatest.{ EitherValues, Matchers, WordSpec }
import play.api.libs.json.{ Json, Writes }

import scala.concurrent.Future

trait BaseSpec
    extends WordSpec
    with ScalatestRouteTest
    with TestKitBase
    with ImplicitSender
    with Matchers
    with EitherValues
    with ScalaFutures
    with IdiomaticMockitoFixture {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(Span(3, Seconds))

  implicit val logger: LoggingAdapter = system.log
  implicit lazy val timeout: Timeout = Timeout(patienceConfig.timeout)
  implicit val ec: MessageDispatcher = system.dispatchers.lookup(CallingThreadDispatcher.Id)
  implicit val scheduler: Scheduler = system.scheduler

  protected val conf: Config = ConfigFactory.load()

  def future[A](a: A): Future[A] = Future.successful(a)

  def future[A](throwable: Throwable): Future[A] = Future.failed[A](throwable)

  def httpEntity[A: Writes](entity: A): HttpEntity.Strict =
    HttpEntity(ContentTypes.`application/json`, Json.toJson(entity).toString)

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
  }

}
