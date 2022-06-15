package baile

import akka.actor.SupervisorStrategy.Escalate
import akka.actor.{ Actor, ActorInitializationException, ActorRef, AllForOneStrategy, PoisonPill, Props, Scheduler, Terminated }
import akka.pattern.ask
import akka.dispatch.MessageDispatcher
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.{ CallingThreadDispatcher, ImplicitSender, TestKit, TestKitBase }
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }
import org.scalatest.{ EitherValues, Matchers, TryValues, WordSpec }
import play.api.libs.json.{ Json, Writes }

import scala.concurrent.{ Future, Promise }

// TODO Make this BaseSpec (remove current BaseSpec and rename this one to BaseSpec) and mix in IdiomaticMockitoFixture.
// TODO After that, remove ExtendedBaseSpec and ExtendedRoutesSpec also.
trait CommonSpec extends WordSpec
  with ScalatestRouteTest
  with TestKitBase
  with ImplicitSender
  with Matchers
  with ScalaFutures
  with EitherValues
  with TryValues {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(3, Seconds))

  implicit val logger: LoggingAdapter = system.log
  implicit lazy val timeout: Timeout = Timeout(patienceConfig.timeout)
  implicit val ec: MessageDispatcher = system.dispatchers.lookup(CallingThreadDispatcher.Id)
  implicit val scheduler: Scheduler = system.scheduler

  protected val conf: Config = ConfigFactory.load()

  def future[A](a: A): Future[A] = Future.successful(a)

  def future[A](throwable: Throwable): Future[A] = Future.failed[A](throwable)

  def httpEntity[A: Writes](entity: A): HttpEntity.Strict =
    HttpEntity(ContentTypes.`application/json`, Json.toJson(entity).toString)

  def doWithActor[T](props: Props)(f: ActorRef => Future[T]): Future[T] = {
    val p = Promise[Unit]()
    val supervisor = system.actorOf(Props(new Actor {

      override def receive: Receive = {
        case props: Props =>
          val actor = context.actorOf(props)
          context.watch(actor)
          sender() ! actor
        case Terminated(_) =>
          p.success(())
      }

      override val supervisorStrategy: AllForOneStrategy =
        AllForOneStrategy() {
          case aie: ActorInitializationException =>
            val betterMessage = s"${ aie.getMessage } Cause: ${ aie.getCause.getMessage }"
            val betterAie = new ActorInitializationException(aie.getActor, betterMessage, aie.getCause) { }
            p.failure(betterAie)
            Escalate
          case e: Exception =>
            p.failure(e)
            Escalate
        }

    }))

    for {
      actor <- (supervisor ? props).mapTo[ActorRef]
      result <- f(actor)
      _ = actor ! PoisonPill
      _ <- p.future
    } yield result

  }

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
  }

}
