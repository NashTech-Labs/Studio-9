package baile.services.dcproject

import java.time.Instant

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.scaladsl.Source
import baile.ExtendedBaseSpec
import baile.RandomGenerators.{ randomPath, randomString }
import baile.dao.dcproject.{ DCProjectDao, SessionDao }
import baile.daocommons.WithId
import baile.domain.dcproject.{ DCProject, DCProjectStatus, Session }
import baile.services.usermanagement.util.TestData.SampleUser
import baile.services.dcproject.SessionMonitor.SubscribeSession
import baile.services.gemini.GeminiService
import cortex.api.gemini.SessionStatus

import scala.concurrent.Future
import scala.concurrent.duration._

class SessionMonitorSpec extends ExtendedBaseSpec {

  trait Setup {

    val dcProjectDao: DCProjectDao = mock[DCProjectDao]
    val sessionDao: SessionDao = mock[SessionDao]
    val geminiService: GeminiService = mock[GeminiService]
    val startupRequestTimeout: FiniteDuration = 50.milliseconds
    val dateTime: Instant = Instant.now()
    val dcProjectName: String = randomString()
    val dcProjectBasePath: String = randomPath()
    val sessionId: String = randomString()
    val geminiSessionId: String = randomString()
    val geminiSessionToken: String = randomString()
    val geminiSessionUrl: String = randomString()
    val dcProjectId: String = randomString()
    val dcProjectWithId = WithId(
      DCProject(
        name = dcProjectName,
        created = dateTime,
        updated = dateTime,
        ownerId = SampleUser.id,
        status = DCProjectStatus.Idle,
        description = None,
        basePath = dcProjectBasePath,
        packageName = None,
        latestPackageVersion = None
      ), dcProjectId
    )

    val session = WithId(
      Session(
        geminiSessionId = geminiSessionId,
        geminiSessionToken = geminiSessionToken,
        geminiSessionUrl = geminiSessionUrl,
        dcProjectId = dcProjectId,
        created = dateTime
      ),
      sessionId
    )

    sessionDao.listAll(*, *)(*) shouldReturn future(Seq.empty)

    def doAfterInstantiate[T](f: ActorRef => Future[T]): T =
      doWithActor(
        SessionMonitor.props(
          dcProjectDao = dcProjectDao,
          sessionDao = sessionDao,
          geminiService = geminiService,
          startupRequestTimeout = startupRequestTimeout
        )
      )(f).futureValue
  }

  "SessionMonitor" should {
    "be instantiated as an actor" in new Setup {
      doAfterInstantiate(_ => future(()))
    }

    "subscribe for new session" in new Setup {
      val source = Source(List(SessionStatus.Completed))
      geminiService.getSessionStatusesSource(
        session.entity.geminiSessionId
      ) shouldReturn future(source)
      sessionDao.delete(session.id)(*) shouldReturn future(true)
      sessionDao.delete(session.id)(*) isLenient()

      doAfterInstantiate { monitor =>
        monitor ? SubscribeSession(
          session = session
        )
      }
    }

  }

}
