package baile.services.process

import java.util.UUID

import akka.actor.Props
import akka.pattern.ask
import baile.BaseSpec
import baile.domain.job.CortexJobStatus
import baile.services.process.JobResultHandler.{ HandleException, HandleJobResult }
import play.api.libs.json._

class JobResultHandlerSpec extends BaseSpec {

  "JobResultHandler ! HandleResult" should {

    val jobId = UUID.randomUUID

    "execute handleResult method with parsed meta" in {
      val handler = system.actorOf(Props(new SampleJobResultHandler(new SampleJobResultHandlerDependency)))
      (handler ? HandleJobResult(
        jobId,
        CortexJobStatus.Completed,
        Json.parse("""{ "data" : [23, 42] }""").as[JsObject]
      )).futureValue
    }

    "throw exception when meta is in incorrect format" in {
      val handler = system.actorOf(Props(new SampleJobResultHandler(new SampleJobResultHandlerDependency)))
      val result = handler ? HandleJobResult(
        jobId,
        CortexJobStatus.Completed,
        Json.parse("""{}""").as[JsObject]
      )

      whenReady(result.failed)(_ shouldBe a[MetaParsingException])
    }

  }

  "JobResultHandler ! HandleException" should {

    "execute handleException method with parsed meta" in {
      val handler = system.actorOf(Props(new SampleJobResultHandler(new SampleJobResultHandlerDependency)))
      (handler ? HandleException(Json.parse("""{ "data" : [23, 42] }""").as[JsObject])).futureValue
    }

    "throw exception when meta is in incorrect format" in {
      val handler = system.actorOf(Props(new SampleJobResultHandler(new SampleJobResultHandlerDependency)))
      val result = handler ? HandleException(Json.parse("""{}""").as[JsObject])

      whenReady(result.failed)(_ shouldBe a[MetaParsingException])
    }

  }

}
