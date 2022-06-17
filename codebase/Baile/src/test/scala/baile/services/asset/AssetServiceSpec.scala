package baile.services.asset

import java.time.Instant
import java.util.UUID

import baile.BaseSpec
import baile.dao.asset.Filters.{ OwnerIdIs, SearchQuery }
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.WithId
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.SortBy
import baile.domain.project.Project
import baile.domain.usermanagement.{ RegularUser, User }
import baile.services.asset.SampleAssetService.SampleAssetError
import baile.services.project.ProjectService
import baile.services.usermanagement.util.TestData.SampleUser
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.ExecutionContext

class AssetServiceSpec extends BaseSpec with BeforeAndAfterEach {
  private val dao = mock[MongoEntityDao[SampleAsset]]
  private val projectService = mock[ProjectService]
  private val assetService = new SampleAssetService(dao, projectService)

  implicit private val user: RegularUser = SampleUser

  private val asset = WithId(SampleAsset(
    bar = "a",
    baz = 1,
    ownerId = user.id
  ), "f")
  private val project = WithId(Project(
    name = "demo",
    created = Instant.now(),
    updated = Instant.now(),
    ownerId = user.id,
    folders = Seq.empty,
    assets = Seq.empty
  ), "id")

  override def beforeEach(): Unit = {
    when(projectService.get(any[String])(any[User])).thenReturn(future(Right(project)))
    reset(dao)
    when(dao.get(any[String])(any[ExecutionContext])).thenReturn(future(None))
    when(dao.get(eqTo(asset.id))(any[ExecutionContext])).thenReturn(future(Some(asset)))
    when(dao.delete(any[String])(any[ExecutionContext])).thenReturn(future(true))
    when(dao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(1))
    when(dao.list(
      any[Filter],
      any[Int],
      any[Int],
      any[Option[SortBy]]
    )(any[ExecutionContext])).thenReturn(future(Seq(asset)))
    when(dao.count(
      any[Filter]
    )(any[ExecutionContext])).thenReturn(future(1))
  }

  "AssetService#get" should {
    "get object from DAO" in {
      whenReady(assetService.get("f")) {
        _ shouldBe Right(asset)
      }
    }

    "return notFound when dao returns nothing" in {
      whenReady(assetService.get("notF")) {
        _ shouldBe Left(SampleAssetError.AssetNotFound)
      }
    }

    "return access denied when owner missmatch" in {
      whenReady(assetService.get("f")(user.copy(id = UUID.randomUUID))) {
        _ shouldBe Left(SampleAssetError.AccessDenied)
      }
    }
  }

  "AssetService#count" should {
    "count object in DAO" in {
      whenReady(assetService.count(Some("f"), Some("a"), Some("b"))) {
        _ shouldBe Right(1)
      }
    }
  }

  "AssetService#list" should {
    "get objects from DAO" in {
      whenReady(assetService.list(Some(""), Nil, 1, 10, None, None)) {
        _ shouldBe Right((Seq(asset), 1))
      }
    }

    "pass paging parameters to DAO" in {
      whenReady(assetService.list(Some(""), Nil, 5, 100, None, None)) { _ =>
        verify(dao).list(any[Filter], eqTo(5), eqTo(100), any[Option[SortBy]])(any[ExecutionContext])
      }
    }

    "pass SearchQuery as a filter" in {
      whenReady(assetService.list(Some("foo"), Nil, 5, 100, None, None)) { _ =>
        verify(dao).list(
          filterContains(SearchQuery("foo")),
          any[Int],
          any[Int],
          any[Option[SortBy]]
        )(any[ExecutionContext])
      }
    }

    "pass user id as a filter" in {
      whenReady(assetService.list(Some("foo"), Nil, 5, 100, None, None)) { _ =>
        verify(dao).list(
          filterContains(OwnerIdIs(user.id)),
          any[Int],
          any[Int],
          any[Option[SortBy]]
        )(any[ExecutionContext])
      }
    }
  }

  "AssetService#delete" should {
    "delete object from DAO" in {
      whenReady(assetService.delete("f")) {
        _ shouldBe Right(())
      }
    }

    "return access denied when owner missmatch" in {
      whenReady(assetService.delete("f")(user.copy(id = UUID.randomUUID))) {
        _ shouldBe Left(SampleAssetError.AccessDenied)
      }
    }
  }

}
