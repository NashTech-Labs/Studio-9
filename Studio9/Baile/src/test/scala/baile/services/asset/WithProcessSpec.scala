package baile.services.asset

import java.util.UUID

import baile.BaseSpec
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.WithId
import baile.domain.asset.AssetReference
import baile.domain.usermanagement.User
import baile.services.usermanagement.util.TestData.SampleUser
import baile.services.asset.AssetService.WithProcess
import baile.services.asset.SampleAssetService.SampleAssetError
import baile.services.process.ProcessService
import baile.services.process.ProcessService.ActionForbiddenError
import baile.services.project.ProjectService
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.{ reset, verify, when }
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.{ ExecutionContext, Future }

class WithProcessSpec extends BaseSpec with BeforeAndAfterEach { spec =>

  private val processService = mock[ProcessService]
  private val dao = mock[MongoEntityDao[SampleAsset]]
  private val mockedProjectService = mock[ProjectService]

  private class SampleWithProcessService extends SampleAssetService(dao, mockedProjectService)
    with WithProcess[SampleAsset, SampleAssetError] {
    override protected val processService: ProcessService = spec.processService

    override protected[services] def preDelete(
      asset: WithId[SampleAsset]
    )(implicit user: User): Future[Either[SampleAssetError, Unit]] = super.preDelete(asset)

  }

  private val withProcess = new SampleWithProcessService()

  private implicit val user: User = SampleUser
  private val asset = randomAsset()
  private val notOwnedAsset = randomAsset()

  override def beforeEach(): Unit = {
    reset(dao)
    reset(processService)
    when(dao.get(eqTo(asset.id))(any[ExecutionContext])).thenReturn(future(Some(asset)))
    when(mockedProjectService.removeAssetFromAllProjects(any[AssetReference])(any[User])).thenReturn(future(()))
    when(processService.cancelProcesses(eqTo(asset.id), eqTo(withProcess.assetType))(eqTo(user)))
      .thenReturn(future(Right(())))
    when(processService.cancelProcesses(eqTo(notOwnedAsset.id), eqTo(withProcess.assetType))(eqTo(user)))
      .thenReturn(future(Left(ActionForbiddenError)))
  }

  "WithProcess#preDelete" should {

    "call processService.cancelProcesses" in {
      whenReady(withProcess.preDelete(asset)){ _ =>
        verify(processService).cancelProcesses(
          eqTo(asset.id), eqTo(withProcess.assetType)
        )(eqTo(user))
      }
    }

    "return OK when cancellation succeeded" in {
      whenReady(withProcess.preDelete(asset))(_ shouldBe Right(()))
    }

    "return OK when cancellation failed" in {
      whenReady(withProcess.preDelete(notOwnedAsset))(_ shouldBe Right(()))
    }

  }

  private def randomAsset(ownerId: UUID = UUID.randomUUID): WithId[SampleAsset] = WithId(
    SampleAsset(
      bar = randomString(),
      baz = randomInt(50),
      ownerId = ownerId,
      name = randomString()
    ),
    randomString(10)
  )
}
