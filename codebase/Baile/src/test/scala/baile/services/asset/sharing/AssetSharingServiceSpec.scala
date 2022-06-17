package baile.services.asset.sharing

import java.time.Instant
import java.util.UUID

import baile.BaseSpec
import baile.dao.asset.sharing._
import baile.daocommons.WithId
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.SortBy
import baile.domain.asset.AssetType.Table
import baile.domain.asset.sharing.SharedResource
import baile.domain.usermanagement.{ RegularUser, User }
import baile.services.asset.sharing.AssetSharingService.AssetSharingServiceError
import baile.services.asset.sharing.AssetSharingService.AssetSharingServiceError._
import baile.services.usermanagement.UmService
import baile.services.usermanagement.UmService.GetUserError
import baile.services.usermanagement.util.TestData
import baile.utils.MailService
import cats.implicits._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._

import scala.concurrent.ExecutionContext
import scala.util.Try

class AssetSharingServiceSpec extends BaseSpec {

  implicit val user: RegularUser = TestData.SampleUser

  val recipientId: UUID = java.util.UUID.randomUUID()

  lazy val sharedResourceEntity: SharedResource = SharedResource(
    ownerId = user.id,
    name = Some("name"),
    created = dateTime,
    updated = dateTime,
    recipientId = Option(recipientId),
    recipientEmail = Some("recipientEmail"),
    assetType = Table,
    assetId = "assetId"
  )
  val mockedMailService: MailService = mock[MailService]
  val mockedSharedResDao: SharedResourceDao = mock[SharedResourceDao]
  val mockedUmService: UmService = mock[UmService]
  val assetSharingService = new AssetSharingService(conf, mockedMailService, mockedUmService, mockedSharedResDao)
  val id = "1"
  val dateTime: Instant = Instant.now()
  val sharedResourceWithId: WithId[SharedResource] = WithId[SharedResource](sharedResourceEntity, id.toString)
  val secondUserId: UUID = java.util.UUID.randomUUID()
  val secondUser: RegularUser = user.copy(id = secondUserId)

  val dummyCreatedId = "1"
  when(mockedSharedResDao.create(any[String => SharedResource])(any[ExecutionContext])).thenAnswer { call =>
    future(WithId(call.getArgument[String => SharedResource](0).apply(dummyCreatedId), dummyCreatedId))
  }
  when(mockedSharedResDao.get(any[Filter])(any[ExecutionContext])) thenReturn future(None)

  when(mockedMailService.sendHtmlFormattedEmail(anyString(), anyString(), anyString(), anyString())).thenReturn {
    Try(())
  }

  "AssetSharingService#createSharedResource" should {

    "create shared resource successfully when recipient id is provided" in {
      when(mockedUmService.getUser(any[UUID])).thenReturn(future(user.asRight))

      val createRes = assetSharingService.create(
        sharedResourceEntity.name,
        sharedResourceEntity.recipientId,
        sharedResourceEntity.recipientEmail,
        sharedResourceEntity.assetType,
        sharedResourceEntity.assetId
      )
      whenReady(createRes) { result =>
        assert(result.isRight)
        assert(result.right.get.entity.name == sharedResourceEntity.name)
      }
    }

    "create shared resource successfully when recipient email is provided" in {
      when(mockedUmService.findUsers(
        any[Option[String]],
        any[Option[String]],
        any[Option[String]],
        any[Option[String]],
        anyInt(),
        anyInt()
      )).thenReturn(future(Seq(user)))
      val createRes = assetSharingService.create(
        sharedResourceEntity.name,
        None,
        sharedResourceEntity.recipientEmail,
        sharedResourceEntity.assetType,
        sharedResourceEntity.assetId
      )
      whenReady(createRes) { result =>
        assert(result.isRight)
        assert(result.right.get.entity.name == sharedResourceEntity.name)
      }
    }

    "create shared resource successfully when recipient email is provided but user not found" in {
      when(mockedUmService.findUsers(
        any[Option[String]],
        any[Option[String]],
        any[Option[String]],
        any[Option[String]],
        anyInt(),
        anyInt()
      )).thenReturn(future(Seq.empty))
      val createRes = assetSharingService.create(
        sharedResourceEntity.name,
        None,
        sharedResourceEntity.recipientEmail,
        sharedResourceEntity.assetType,
        sharedResourceEntity.assetId
      )
      whenReady(createRes) { result =>
        assert(result.isRight)
        assert(result.right.get.entity.name == sharedResourceEntity.name)
      }
    }

    "not create shared resource when nor recipient email neither id is provided" in {
      when(mockedMailService.sendHtmlFormattedEmail(anyString(), anyString(), anyString(), anyString())).thenReturn {
        Try(())
      }
      when(mockedSharedResDao.get(any[Filter])(any[ExecutionContext])) thenReturn future(None)

      val createRes = assetSharingService.create(
        sharedResourceEntity.name,
        None,
        None,
        sharedResourceEntity.assetType,
        sharedResourceEntity.assetId
      )
      whenReady(createRes) { result =>
        assert(result.isLeft)
        assert(result.left.get === RecipientIsNotSpecified)
      }
    }

    "not create shared resource if it is already shared with resource" in {

      val dummyCreatedId = "1"
      when(mockedSharedResDao.create(any[SharedResource])(any[ExecutionContext])).thenReturn(
        future(dummyCreatedId)
      )
      when(mockedSharedResDao.get(any[Filter])(any[ExecutionContext])) thenReturn future(Some(sharedResourceWithId))
      when(mockedMailService.sendHtmlFormattedEmail(anyString(), anyString(), anyString(), anyString())).thenReturn {
        Try(())
      }
      when(mockedUmService.getUser(any[UUID])).thenReturn(future(user.asRight))
      val createRes = assetSharingService.create(
        sharedResourceEntity.name,
        sharedResourceEntity.recipientId,
        sharedResourceEntity.recipientEmail,
        sharedResourceEntity.assetType,
        sharedResourceEntity.assetId
      )
      whenReady(createRes) { result =>
        assert(result.isLeft)
        assert(result.left.get === AlreadyShared)
      }
    }

  }

  "AssetSharingService#deleteSharesForAsset" should {

    "delete shares for Asset" in {
      when(mockedSharedResDao.deleteMany(any[Filter])(any[ExecutionContext])) thenReturn future(1)
      assetSharingService.deleteSharesForAsset(id, Table).futureValue
    }

  }

  "AssetSharingService#getSharedResourcesWhereOwner" should {

    when(mockedSharedResDao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(1))

    "get sharedResources on the basis of owner when assetId and assetType both provided" in {
      val assetId = "dummyAssetId"
      val assetType = Table
      val id = user.id
      when(mockedSharedResDao.listAll(any[Filter], any[Option[SortBy]])(any[ExecutionContext])) thenReturn
        future(Seq(WithId[SharedResource](sharedResourceEntity, id.toString)))
      val response = assetSharingService.list(Some(assetId), Some(assetType))(user)
      whenReady(response) { value =>
        assert(value.isRight)
        assert(value.right.exists(_._1.head.entity.ownerId == sharedResourceEntity.ownerId))
      }
    }

    "get sharedResources on the basis of owner when only assetId provided" in {
      val assetId = "dummyAssetId"
      val id = user.id
      when(mockedSharedResDao.listAll(any[Filter], any[Option[SortBy]])(any[ExecutionContext])) thenReturn
        future(Seq(WithId[SharedResource](sharedResourceEntity, id.toString)))
      val response = assetSharingService.list(Some(assetId), None)(user)
      whenReady(response) { value =>
        assert(value.isRight)
        assert(value.right.exists(_._1.head.entity.ownerId == sharedResourceEntity.ownerId))
      }
    }

    "get sharedResources on the basis of owner when only assetType provided" in {
      val assetType = Table
      val id = user.id
      when(mockedSharedResDao.listAll(any[Filter], any[Option[SortBy]])(any[ExecutionContext])) thenReturn
        future(Seq(WithId[SharedResource](sharedResourceEntity, id.toString)))
      val response = assetSharingService.list(None, Some(assetType))(user)
      whenReady(response) { value =>
        assert(value.isRight)
        assert(value.right.exists(_._1.head.entity.ownerId == sharedResourceEntity.ownerId))
      }
    }

    "get sharedResources on the basis of owner when neither AssetId nor AssetType provided" in {
      val id = user.id
      when(mockedSharedResDao.listAll(any[Filter], any[Option[SortBy]])(any[ExecutionContext])) thenReturn
        future(Seq(WithId[SharedResource](sharedResourceEntity, id.toString)))
      val response = assetSharingService.list(None, None)(user)
      whenReady(response) { value =>
        assert(value.isRight)
        assert(value.right.exists(_._1.head.entity.ownerId == sharedResourceEntity.ownerId))
      }
    }
  }

  "AssetSharingService#getSharedResourcesWhereRecipient" should {

    "get shared resource on the basis of recipient" in {
      val id = user.id
      when(mockedSharedResDao.listAll(any[Filter], any[Option[SortBy]])(any[ExecutionContext])) thenReturn
        future(Seq(WithId[SharedResource](sharedResourceEntity, id.toString)))

      val response = assetSharingService.listAll(id)
      whenReady(response) { value =>
        assert(value.head.entity.ownerId == sharedResourceEntity.ownerId)
      }
    }
  }

  "AssetSharingService#listAll(recipient, assetType)" should {
    "return shared resources" in {
      when(mockedSharedResDao.listAll(any[Filter], any[Option[SortBy]])(any[ExecutionContext]))
        .thenReturn(future(Seq(sharedResourceWithId)))
      whenReady(assetSharingService.listAll(recipientId, sharedResourceEntity.assetType))(
        _ shouldBe Seq(sharedResourceWithId)
      )
    }
  }

  "AssetSharingService#getSharedResourceById" should {

    "get shared resource By id" in {
      val id = user.id
      when(mockedSharedResDao.get(anyString())(any[ExecutionContext])) thenReturn
        future(Some(WithId[SharedResource](sharedResourceEntity, id.toString)))
      val response = assetSharingService.get(id.toString)
      whenReady(response) { value =>
        assert(value.isRight)
        assert(value.right.get.entity.ownerId == sharedResourceEntity.ownerId)
      }
    }

  }

  "AssetSharingService#deleteSharedResource" should {

    "return success response from delete shared resource" in {
      val id = user.id
      when(mockedSharedResDao.get(anyString())(any[ExecutionContext])) thenReturn
        future(Some(WithId[SharedResource](sharedResourceEntity, id.toString)))
      when(mockedSharedResDao.delete(anyString())(any[ExecutionContext])) thenReturn future(true)
      val response = assetSharingService.delete(id.toString)(user)
      whenReady(response) { value =>
        assert(value.isRight)
      }
    }

    "return error response from delete shared resource when ownerId do'snt match with user id" in {
      val id = user.id
      val ownerId = java.util.UUID.randomUUID()
      val sharedResource = sharedResourceEntity.copy(ownerId = ownerId)
      when(mockedSharedResDao.get(anyString())(any[ExecutionContext])) thenReturn
        future(Some(WithId[SharedResource](sharedResource, id.toString)))
      val response = assetSharingService.delete(id.toString)(user)
      whenReady(response) { value =>
        assert(value.isLeft)
        assert(value.left.get === AccessDenied)
      }

    }

    "return error response from delete shared resource when user not found" in {
      when(mockedSharedResDao.get(anyString())(any[ExecutionContext])) thenReturn
        future(None)
      val response = assetSharingService.delete(anyString())(any[User])
      whenReady(response) { value =>
        assert(value.isLeft)
        assert(value.left.get === ResourceNotFound)
      }

    }

    "return error response from delete shared resource when no data deleted" in {
      val id = user.id
      when(mockedSharedResDao.get(anyString())(any[ExecutionContext])) thenReturn
        future(Some(WithId[SharedResource](sharedResourceEntity, id.toString)))
      when(mockedSharedResDao.delete(anyString())(any[ExecutionContext])) thenReturn future(false)
      val response = assetSharingService.delete(id.toString)(user)
      whenReady(response) { value =>
        assert(value.isRight)
      }
    }

  }

  "AssetSharingService#updateSharesForNewUser" should {

    "update resource" in {
      val id = user.id
      when(
        mockedSharedResDao.updateMany(any[Filter], any[SharedResource => SharedResource].apply)(any[ExecutionContext]))
        .thenReturn(future(1))
      whenReady(assetSharingService.updateSharesForNewUser("email", id)) { result =>
        assert(result == 1)
      }
    }

  }

  "AssetSharingService#getRecipient" should {

    "get user" in {
      val id = user.id
      when(mockedSharedResDao.get(anyString())(any[ExecutionContext])) thenReturn
        future(Some(WithId[SharedResource](sharedResourceEntity, id.toString)))
      when(mockedUmService.getUser(any[UUID])) thenReturn future(user.asRight)
      val response = assetSharingService.getRecipient(id.toString)(user)
      whenReady(response) { value =>
        assert(value.isRight)
        assert(value.right.exists(_ === user))
      }
    }

    "not be able to get user as recipientId does not exists" in {
      val id = user.id
      when(mockedSharedResDao.get(anyString())(any[ExecutionContext])) thenReturn
        future(Some(WithId[SharedResource](sharedResourceEntity.copy(recipientId = None), id.toString)))
      val response = assetSharingService.getRecipient(id.toString)(user)
      whenReady(response) { value =>
        assert(value.isLeft)
        assert(value.left.exists(_ === AssetSharingServiceError.RecipientNotFound))
      }
    }

    "not be able to get user as user with given recipientId does not exists" in {
      val id = user.id
      when(mockedSharedResDao.get(anyString())(any[ExecutionContext])) thenReturn
        future(Some(WithId[SharedResource](sharedResourceEntity, id.toString)))
      when(mockedUmService.getUser(any[UUID])) thenReturn future(GetUserError.UserNotFound.asLeft)
      val response = assetSharingService.getRecipient(id.toString)(user)
      whenReady(response) { value =>
        assert(value.isLeft)
        assert(value.left.exists(_ === AssetSharingServiceError.RecipientNotFound))
      }
    }

  }

  "AssetSharingService#getOwner" should {

    "get user" in {
      val id = user.id
      when(mockedSharedResDao.get(anyString())(any[ExecutionContext])) thenReturn
        future(Some(WithId[SharedResource](sharedResourceEntity, id.toString)))
      when(mockedUmService.getUser(any[UUID])) thenReturn future(user.asRight)
      val response = assetSharingService.getOwner(id.toString)(user)
      whenReady(response) { value =>
        assert(value.isRight)
        assert(value.right.exists(_ === user))
      }
    }

    "not be able to get user as user with given owner id does not exists" in {
      val id = user.id
      when(mockedSharedResDao.get(anyString())(any[ExecutionContext])) thenReturn
        future(Some(WithId[SharedResource](sharedResourceEntity, id.toString)))
      when(mockedUmService.getUser(any[UUID])) thenReturn future(GetUserError.UserNotFound.asLeft)
      val response = assetSharingService.getOwner(id.toString)(user)
      whenReady(response) { value =>
        assert(value.isLeft)
        assert(value.left.exists(_ === AssetSharingServiceError.OwnerNotFound))
      }
    }

  }

}
