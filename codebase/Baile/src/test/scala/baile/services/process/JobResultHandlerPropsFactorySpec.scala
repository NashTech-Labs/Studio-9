package baile.services.process

import java.util.UUID

import akka.actor.Props
import baile.BaseSpec
import baile.domain.job.CortexJobTerminalStatus
import play.api.libs.json.{ JsError, JsValue, Reads }

import scala.concurrent.{ ExecutionContext, Future }

class JobResultHandlerPropsFactorySpec extends BaseSpec {

  "JobResultHandlerPropsFactory#apply" should {

    "return success of props if a value of exact type is found in sources" in {
      JobResultHandlerPropsFactory(
        classOf[SampleJobResultHandler].getCanonicalName,
        new SampleDependencyProvider
      ).get shouldBe a[Props]
    }

    "return success of props if an argument value of subtype is found in sources" in {
      JobResultHandlerPropsFactory(
        classOf[SampleJobResultHandler].getCanonicalName,
        new AnotherDependencyProvider
      ).get shouldBe a[Props]
    }

    "return failure when no argument value is found in sources" in {
      val exception = JobResultHandlerPropsFactory(
        classOf[SampleJobResultHandler].getCanonicalName,
        "foo"
      ).failed.get

      exception shouldBe a[RuntimeException]
      exception.getMessage should include ("Not found argument for parameter")
    }

    "return failure when multiple argument values of exact type are found in sources" in {
      val exception = JobResultHandlerPropsFactory(
        classOf[SampleJobResultHandler].getCanonicalName,
        new SampleDependencyProvider,
        new SampleDependencyProvider
      ).failed.get

      exception shouldBe a[RuntimeException]
      exception.getMessage should include ("Found multiple arguments for parameter")
    }

    "return failure when multiple argument values of subtype are found in sources" in {
      val exception = JobResultHandlerPropsFactory(
        classOf[SampleJobResultHandler].getCanonicalName,
        new SampleDependencyProvider,
        new AnotherDependencyProvider
      ).failed.get

      exception shouldBe a[RuntimeException]
      exception.getMessage should include ("Found multiple arguments for parameter")
    }

    "return failure for classes with multiple constructors" in {

      val exception = JobResultHandlerPropsFactory(
        classOf[AnotherJobResultHandler].getCanonicalName,
        new SampleDependencyProvider
      ).failed.get

      exception shouldBe a[RuntimeException]
      exception.getMessage should include ("Only one constructor allowed")
    }

  }

}

class AnotherJobResultHandler(s: String) extends JobResultHandler[Any] {

  def this() = this("foo")

  override protected val metaReads: Reads[Any] = (json: JsValue) => JsError()

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Any
  )(implicit ec: ExecutionContext): Future[Unit] = Future.failed(new RuntimeException)

  override protected def handleException(meta: Any): Future[Unit] = Future.failed(new RuntimeException)

}

class SampleDependencySubType extends SampleJobResultHandlerDependency

class AnotherDependencyProvider {
  lazy val sampleDependency = new SampleDependencySubType
}
