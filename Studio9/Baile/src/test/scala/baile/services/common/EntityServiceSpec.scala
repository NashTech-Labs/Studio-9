package baile.services.common

import akka.event.LoggingAdapter
import baile.BaseSpec
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.WithId
import baile.daocommons.filters.{ Filter, TrueFilter }
import baile.daocommons.sorting.SortBy
import baile.domain.usermanagement.User
import baile.services.usermanagement.util.TestData.SampleUser
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.ExecutionContext

class EntityServiceSpec extends BaseSpec with BeforeAndAfterEach { self =>
  private val dao: MongoEntityDao[Foo] = mock[MongoEntityDao[Foo]]

  private val service: EntityService[Foo, FooError] = new EntityService[Foo, FooError] {
    override implicit val logger: LoggingAdapter = self.logger
    override implicit val ec: ExecutionContext = implicitly[ExecutionContext]
    override val notFoundError: FooError = FooError.NotFound
    override protected val dao: MongoEntityDao[Foo] = self.dao
  }

  private val entity = WithId(Foo(
    bar = "a",
    baz = 1
  ), "f")
  implicit private val user: User = SampleUser

  override def beforeEach(): Unit = {
    reset(dao)
    when(dao.get(any[String])(any[ExecutionContext])).thenReturn(future(None))
    when(dao.get(eqTo(entity.id))(any[ExecutionContext])).thenReturn(future(Some(entity)))
    when(dao.delete(any[String])(any[ExecutionContext])).thenReturn(future(true))
    when(dao.update(any[String], any[Foo => Foo].apply)(any[ExecutionContext])).thenReturn(future(Some(entity)))
    when(dao.list(
      any[Filter],
      any[Int],
      any[Int],
      any[Option[SortBy]]
    )(any[ExecutionContext])).thenReturn(future(Seq(entity)))
    when(dao.count(
      any[Filter]
    )(any[ExecutionContext])).thenReturn(future(1))
  }

  "EntityService#get" should {
    "get object from DAO" in {
      whenReady(service.get("f")) {
        _ shouldBe Right(entity)
      }
    }

    "return notFound when dao returns nothing" in {
      whenReady(service.get("notF")) {
        _ shouldBe Left(FooError.NotFound)
      }
    }
  }

  "EntityService#list" should {
    "get objects from DAO" in {
      whenReady(service.list(TrueFilter, Nil, 1, 10)) {
        _ shouldBe Right((Seq(entity), 1))
      }
    }

    "pass paging parameters to DAO" in {
      whenReady(service.list(TrueFilter, Nil, 5, 100)) { _ =>
        verify(dao).list(any[Filter], eqTo(5), eqTo(100), any[Option[SortBy]])(any[ExecutionContext])
      }
    }

    "pass filter to DAO" in {
      whenReady(service.list(TrueFilter, Nil, 5, 100)) { _ =>
        verify(dao).list(
          filterContains(TrueFilter),
          any[Int],
          any[Int],
          any[Option[SortBy]]
        )(any[ExecutionContext])
      }
    }
  }

  "EntityService#update" should {
    "update object in DAO" in {
      whenReady(service.update("f", x => x)) {
        _ shouldBe Right(entity)
      }
    }

    "return notFound when dao returns nothing" in {
      whenReady(service.update("notF", x => x)) {
        _ shouldBe Left(FooError.NotFound)
      }
    }
  }

  "EntityService#delete" should {
    "delete object from DAO" in {
      whenReady(service.delete("f")) {
        _ shouldBe Right(())
      }
    }

    "return notFound when dao returns nothing" in {
      whenReady(service.delete("notF")) {
        _ shouldBe Left(FooError.NotFound)
      }
    }
  }

  sealed trait FooError

  object FooError {
    case object NotFound extends FooError
  }

  case class Foo (
    bar: String,
    baz: Int,

    ownerId: String = "owner",
  )

}
