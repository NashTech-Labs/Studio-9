package baile.services.dcproject

import java.time.Instant

import akka.stream.scaladsl.{ Sink, Source }
import akka.testkit.TestProbe
import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.dcproject.{ DCProjectDao, SessionDao }
import baile.daocommons.WithId
import baile.daocommons.filters.IdIs
import baile.domain.dcproject._
import baile.domain.remotestorage.S3TemporaryCredentials
import baile.domain.usermanagement.User
import baile.services.dcproject.SessionService.SessionNodeParams
import baile.services.dcproject.SessionService.SessionServiceError._
import baile.services.gemini.GeminiService
import baile.services.remotestorage.RemoteStorageService
import baile.services.usermanagement.util.TestData.SampleUser
import cortex.api.gemini.{ JupyterSessionResponse, SessionStatus => GeminiSessionStatus }

class SessionServiceSpec extends ExtendedBaseSpec {
  
  trait Setup {

    val dcProjectDao: DCProjectDao = mock[DCProjectDao]
    val geminiService: GeminiService = mock[GeminiService]
    val projectStorage: RemoteStorageService = mock[RemoteStorageService]
    val sessionDao: SessionDao = mock[SessionDao]
    val dateTime: Instant = Instant.now()
    val dcProjectName: String = randomString()
    val dcProjectBasePath: String = randomPath()
    val dcProjectId: String = randomString()
    val interactiveDCProjectId: String = randomString()
    val geminiSessionId: String = randomString()
    val geminiSessionToken: String = randomString()
    val geminiSessionUrl: String = randomString()
    val projectSessionRunningTime: Long = randomInt(10).toLong
    val accessToken: String = randomString()
    val region: String = randomString()
    val accessKey: String = randomString()
    val secretKey: String = randomString()
    val sessionToken: String = randomString()
    val bucketName: String = randomString()
    val projectKeyPrefix: String = randomString()
    val sessionNodeParams: SessionNodeParams = SessionNodeParams(1, 1)

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

    val sessionEntity = WithId(
      Session(
        geminiSessionId = geminiSessionId,
        geminiSessionToken = geminiSessionToken,
        dcProjectId = dcProjectId,
        created = dateTime,
        geminiSessionUrl = geminiSessionUrl
      ), randomString()
    )

    val interactiveDCProjectWithId = WithId(
      dcProjectWithId.entity.copy(status = DCProjectStatus.Interactive),
      interactiveDCProjectId
    )

    val sessionService = new SessionService(
      dcProjectDao,
      geminiService,
      projectStorage,
      sessionDao,
      projectKeyPrefix,
      sessionNodeParams,
      TestProbe().ref
    )

    implicit val user: User = SampleUser

    val s3Credentials = S3TemporaryCredentials(region, bucketName, accessKey, secretKey, sessionToken)

  }

  "SessionService#createSession" should {
    "return successful response" in new Setup {
      dcProjectDao.get(argThat(containsFilter(IdIs(dcProjectWithId.id))))(*) shouldReturn future(Some(dcProjectWithId))
      dcProjectDao.update(dcProjectWithId.id, *)(*) shouldReturn future(Some(dcProjectWithId))
      projectStorage.path(any[String], any[String]) shouldReturn dcProjectBasePath
      projectStorage.getTemporaryCredentials(*, *)(*) shouldReturn future(s3Credentials)
      geminiService.createSession(*) shouldReturn future(JupyterSessionResponse(
        id = geminiSessionId,
        token = geminiSessionToken,
        url = geminiSessionUrl,
        status = GeminiSessionStatus.Queued,
        startedAt = Instant.now()
      ))
      sessionDao.create(*)(*) shouldReturn future(sessionEntity)
      whenReady(
        sessionService.create(dcProjectId, accessToken, runOnGPUNode = true)
      )(_ shouldBe Right(sessionEntity))
    }

    "return ProjectAlreadyInSession if a session is already started for a project" in new Setup {
      dcProjectDao.get(argThat(containsFilter(IdIs(interactiveDCProjectWithId.id))))(*) shouldReturn
        future(Some(interactiveDCProjectWithId))
      whenReady(
        sessionService.create(interactiveDCProjectId, accessToken, runOnGPUNode = true)
      )(_ shouldBe Left(ProjectAlreadyInSession))
    }

  }

  "SessionService#getSessionStatus" should {
    "return successful response" in new Setup {
      import cortex.api.gemini.SessionStatus._
      val geminiSource = Source(List(
        Submitted,
        Running,
        Queued,
        Failed,
        Completed
      ))

      dcProjectDao.get(argThat(containsFilter(IdIs(interactiveDCProjectWithId.id))))(*) shouldReturn
        future(Some(interactiveDCProjectWithId))
      geminiService.getSessionStatusesSource(*) shouldReturn future(geminiSource)
      sessionDao.get(*)(*) shouldReturn future(Some(sessionEntity))
      whenReady(
        sessionService.getStatusesSource(interactiveDCProjectId)
      )(result => {
        result.isRight shouldBe true
        whenReady(result.right.get.runWith(Sink.seq[SessionStatus]))(_ shouldBe List(
          SessionStatus.Submitted,
          SessionStatus.Running,
          SessionStatus.Queued,
          SessionStatus.Failed,
          SessionStatus.Completed
        ))
      })
    }

    "return ProjectNotInSession if the project status field is idle" in new Setup {
      dcProjectDao.get(argThat(containsFilter(IdIs(dcProjectWithId.id))))(*) shouldReturn future(Some(dcProjectWithId))
      whenReady(
        sessionService.getStatusesSource(dcProjectId)
      )(_ shouldBe Left(ProjectNotInSession))
    }

    "return SessionNotFound if we try to get a session which is not yet created for the project" in new Setup {
      dcProjectDao.get(argThat(containsFilter(IdIs(interactiveDCProjectWithId.id))))(*) shouldReturn
        future(Some(interactiveDCProjectWithId))
      sessionDao.get(*)(*) shouldReturn future(None)
      whenReady(
        sessionService.getStatusesSource(interactiveDCProjectId)
      )(_ shouldBe Left(SessionNotFound))
    }

  }

  "SessionService#cancelSession" should {
    "return successful response" in new Setup {
      dcProjectDao.get(argThat(containsFilter(IdIs(interactiveDCProjectWithId.id))))(*) shouldReturn
        future(Some(interactiveDCProjectWithId))
      dcProjectDao.update(interactiveDCProjectWithId.id, *)(*) shouldReturn future(Some(interactiveDCProjectWithId))
      geminiService.cancelSession(*) shouldReturn future(())
      sessionDao.get(*)(*) shouldReturn future(Some(sessionEntity))
      sessionDao.delete(*)(*) shouldReturn future(true)
      whenReady(
        sessionService.cancel(interactiveDCProjectId)
      )(_ shouldBe Right(()))
    }

    "return ProjectNotInSession if the project status field is idle" in new Setup {
      dcProjectDao.get(argThat(containsFilter(IdIs(dcProjectWithId.id))))(*) shouldReturn future(Some(dcProjectWithId))
      whenReady(
        sessionService.cancel(dcProjectId)
      )(_ shouldBe Left(ProjectNotInSession))
    }

  }

}
