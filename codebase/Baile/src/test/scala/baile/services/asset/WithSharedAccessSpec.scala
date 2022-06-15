package baile.services.asset

import java.time.Instant
import java.util.UUID

import baile.BaseSpec
import baile.dao.asset.Filters.{ InLibraryIs, OwnerIdIs }
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.WithId
import baile.daocommons.filters.{ IdIn, TrueFilter }
import baile.daocommons.sorting.SortBy
import baile.domain.asset.AssetScope.{ All, Personal, Shared }
import baile.domain.asset.sharing.SharedResource
import baile.domain.usermanagement.User
import baile.services.asset.AssetService.WithSharedAccess
import baile.services.asset.SampleAssetService.SampleAssetError
import baile.services.asset.sharing.AssetSharingService
import baile.services.asset.sharing.AssetSharingService.AssetSharingServiceError
import baile.services.usermanagement.util.TestData.SampleUser
import baile.services.project.ProjectService
import cats.implicits._
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.{ ExecutionContext, Future }

class WithSharedAccessSpec extends BaseSpec with BeforeAndAfterEach { spec =>

  private val assetSharingService = mock[AssetSharingService]
  private val dao = mock[MongoEntityDao[SampleAsset]]
  private val mockedProjectService = mock[ProjectService]

  private class SampleWithSharedAccessService extends SampleAssetService(dao, mockedProjectService)
    with WithSharedAccess[SampleAsset, SampleAssetError] {

    override protected val assetSharingService: AssetSharingService = spec.assetSharingService

    override protected[services] def preDelete(
      asset: WithId[SampleAsset]
    )(implicit user: User): Future[Either[SampleAssetError, Unit]] = super.preDelete(asset)

  }

  private val withSharedAccess = new SampleWithSharedAccessService

  private implicit val user: User = SampleUser
  private val sharedAsset1 = randomAsset()
  private val sharedAsset2 = randomAsset()
  private val personalAsset1 = randomAsset(user.id)
  private val personalAsset2 = randomAsset(user.id)
  private val nonExistingSharedResourceId = randomString(10)
  private val sharedResource1 = randomSharedResource(sharedAsset1.id)
  private val sharedResource2 = randomSharedResource(sharedAsset2.id)

  private val pageSize = 100
  private val page = 1

  override def beforeEach(): Unit = {
    reset(dao)
    reset(assetSharingService)
    when(dao.get(eqTo(sharedAsset2.id))(any[ExecutionContext])).thenReturn(future(Some(sharedAsset2)))
    when(dao.get(eqTo(sharedAsset1.id))(any[ExecutionContext])).thenReturn(future(Some(sharedAsset1)))
    when(dao.get(eqTo(personalAsset1.id))(any[ExecutionContext])).thenReturn(future(Some(personalAsset1)))
    when(dao.get(eqTo(personalAsset2.id))(any[ExecutionContext])).thenReturn(future(Some(personalAsset2)))
    when(dao.list(
      eqTo(OwnerIdIs(user.id) && (InLibraryIs(true) && TrueFilter)),
      eqTo(page),
      eqTo(pageSize),
      any[Option[SortBy]]
    )(any[ExecutionContext])).thenReturn(future(Seq(personalAsset1, personalAsset2)))
    when(dao.list(
      eqTo(IdIn(Seq(sharedAsset1.id, sharedAsset2.id)) && (InLibraryIs(true) && TrueFilter)),
      eqTo(page),
      eqTo(pageSize),
      any[Option[SortBy]]
    )(any[ExecutionContext])).thenReturn(future(Seq(sharedAsset1, sharedAsset2)))
    when(dao.list(
      eqTo((OwnerIdIs(user.id) || IdIn(Seq(sharedAsset1.id, sharedAsset2.id))) && (InLibraryIs(true) && TrueFilter)),
      eqTo(page),
      eqTo(pageSize),
      any[Option[SortBy]]
    )(any[ExecutionContext])).thenReturn(future(Seq(personalAsset1, personalAsset2, sharedAsset1, sharedAsset2)))
    when(dao.count(
      eqTo(OwnerIdIs(user.id) && (InLibraryIs(true) && TrueFilter))
    )(any[ExecutionContext])).thenReturn(future(2))
    when(dao.count(
      eqTo((InLibraryIs(true) && TrueFilter) && IdIn(Seq(sharedAsset1.id, sharedAsset2.id)))
    )(any[ExecutionContext])).thenReturn(future(2))
    when(dao.count(
      eqTo((OwnerIdIs(user.id) || IdIn(Seq(sharedAsset1.id, sharedAsset2.id))) && (InLibraryIs(true) && TrueFilter))
    )(any[ExecutionContext])).thenReturn(future(4))
    when(assetSharingService.get(eqTo(nonExistingSharedResourceId))(eqTo(user)))
      .thenReturn(future(AssetSharingServiceError.ResourceNotFound.asLeft))
    when(assetSharingService.get(eqTo(sharedResource1.id))(eqTo(user))).thenReturn(future(sharedResource1.asRight))
    when(assetSharingService.get(eqTo(sharedResource2.id))(eqTo(user))).thenReturn(future(sharedResource2.asRight))
    when(assetSharingService.listAll(eqTo(user.id), eqTo(withSharedAccess.assetType)))
      .thenReturn(future(Seq(sharedResource1, sharedResource2)))
    when(assetSharingService.deleteSharesForAsset(any[String], eqTo(withSharedAccess.assetType))(eqTo(user)))
      .thenReturn(future(()))
  }

  "WithSharedAccess#get" should {

    "return shared asset" in {
      whenReady(withSharedAccess.get(sharedAsset1.id, Some(sharedResource1.id)))(_ shouldBe sharedAsset1.asRight)
    }

    "return access denied error when sharedResourceId is not provided" in {
      whenReady(withSharedAccess.get(sharedAsset1.id, None))(_ shouldBe SampleAssetError.AccessDenied.asLeft)
    }

    "return not found error when shared resource was not found" in {
      whenReady(withSharedAccess.get(sharedAsset1.id, Some(nonExistingSharedResourceId)))(
        _ shouldBe SampleAssetError.AssetNotFound.asLeft
      )
    }

    "return access denied error when shared resource is for different asset" in {
      whenReady(withSharedAccess.get(sharedAsset1.id, Some(sharedResource2.id)))(
        _ shouldBe SampleAssetError.AccessDenied.asLeft
      )
    }

  }

  "WithSharedAccess#list" should {

    "return list of personal assets" in {
      whenReady(withSharedAccess.list(
        scope = Some(Personal),
        search = None,
        orderBy = Seq.empty,
        page = page,
        pageSize = pageSize,
        projectId = None,
        folderId = None
      ))(_ shouldBe (Seq(personalAsset1, personalAsset2), 2).asRight)
    }

    "return list of shared assets" in {
      whenReady(withSharedAccess.list(
        scope = Some(Shared),
        search = None,
        orderBy = Seq.empty,
        page = page,
        pageSize = pageSize,
        projectId = None,
        folderId = None
      ))(_ shouldBe (Seq(sharedAsset1, sharedAsset2), 2).asRight)
    }

    "return list of all assets" in {
      whenReady(withSharedAccess.list(
        scope = Some(All),
        search = None,
        orderBy = Seq.empty,
        page = page,
        pageSize = pageSize,
        projectId = None,
        folderId = None
      ))(_ shouldBe (Seq(personalAsset1, personalAsset2, sharedAsset1, sharedAsset2), 4).asRight)
    }

  }

  "WithSharedAccess#count" should {

    "return count of personal assets" in {
      whenReady(withSharedAccess.count(
        scope = Some(Personal),
        search = None,
        projectId = None,
        folderId = None
      ))(_ shouldBe 2.asRight)
    }

    "return count of shared assets" in {
      whenReady(withSharedAccess.count(
        scope = Some(Shared),
        search = None,
        projectId = None,
        folderId = None
      ))(_ shouldBe 2.asRight)
    }

    "return count of all assets" in {
      whenReady(withSharedAccess.count(
        scope = Some(All),
        search = None,
        projectId = None,
        folderId = None
      ))(_ shouldBe 4.asRight)
    }

  }


  "WithProcess#preDelete" should {

    "call assetSharingService.deleteSharesForAsset" in {
      whenReady(withSharedAccess.preDelete(sharedAsset1)){ _ =>
        verify(assetSharingService).deleteSharesForAsset(
          eqTo(sharedAsset1.id), eqTo(withSharedAccess.assetType)
        )(eqTo(user))
      }
    }

    "return OK when delete succeeded" in {
      whenReady(withSharedAccess.preDelete(sharedAsset1))(_ shouldBe Right(()))
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

  private def randomSharedResource(assetId: String): WithId[SharedResource] = WithId(
    SharedResource(
      ownerId = sharedAsset1.entity.ownerId,
      name = Some(sharedAsset1.entity.name),
      created = Instant.now,
      updated = Instant.now,
      recipientId = Some(user.id),
      recipientEmail = Some(user.email),
      assetType = withSharedAccess.assetType,
      assetId = assetId
    ),
    randomString(10)
  )

}
