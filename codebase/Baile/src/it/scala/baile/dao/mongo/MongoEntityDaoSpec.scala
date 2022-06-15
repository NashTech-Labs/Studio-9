package baile.dao.mongo

import baile.BaseItSpec
import baile.dao.mongo.BsonHelpers._
import baile.daocommons.WithId
import baile.daocommons.exceptions.{ UnknownFilterException, UnknownSortingFieldException }
import baile.daocommons.filters.{ FalseFilter, Filter, IdIn, TrueFilter }
import baile.daocommons.sorting.Direction.Descending
import baile.daocommons.sorting.{ Field, SortBy }
import baile.utils.SampleEntity
import org.bson.BsonString
import org.mongodb.scala._
import org.mongodb.scala.bson.{ BsonArray, ObjectId }
import org.mongodb.scala.model.{ Filters => MongoFilters }

import scala.concurrent.Future
import scala.util.Try

class MongoEntityDaoSpec extends BaseItSpec with DockerizedMongoDB { self =>

  case class NameIs(name: String) extends Filter
  case class ContainsData(part: Int) extends Filter

  case object Name extends Field
  case object Unsupported extends Field

  val collectionName: String = "entities"

  val entity = SampleEntity("foo", List(23, 42))

  class SampleDao extends MongoEntityDao[SampleEntity] {

    override val collectionName: String = self.collectionName
    override protected val database: MongoDatabase = self.database

    override protected def entityToDocument(entity: SampleEntity): Document =
      Document(
        "data" -> entity.data,
        "name" -> entity.name
      )

    override protected def documentToEntity(document: Document): Try[SampleEntity] = Try {
      SampleEntity(
        name = document.getMandatory[BsonString]("name").getValue,
        data = document.getMandatory[BsonArray]("data").map(_.asInt32.getValue).toList
      )
    }

    override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
      case NameIs(name) => Try(MongoFilters.equal("name", name))
      case ContainsData(part) => Try(MongoFilters.equal("data", part))
    }

    override protected val fieldMapper: Map[Field, String] = Map(
      Name -> "name"
    )

    def listDistinctNames(filter: Filter): Future[Seq[String]] =
      listDistinctValues(fieldMapper(Name), filter, (_: BsonString).getValue)

  }

  lazy val dao: SampleDao = new SampleDao

  def createEntity(entity: SampleEntity): Future[String] = dao.create(entity)
  def createEntity(): Future[String] = createEntity(entity)
  def createEntities(entities: Seq[SampleEntity]): Future[Seq[String]] = Future.sequence(entities.map(createEntity))

  after(
    database.drop().toFuture.futureValue
  )

  "MongoEntityDao#insert" should {
    "create new entity in mongo and return its id" in {
      whenReady {
        for {
          id <- createEntity()
          entity <- dao.get(id)
        } yield entity
      } { result =>
        result.map(_.entity) shouldBe Some(entity)
      }
    }
  }

  "MongoEntityDao#get" should {

    "return entity by its id" in {
      whenReady {
        for {
          id <- createEntity()
          withId <- dao.get(id)
        } yield (withId, id)
      } { case (withId, id) =>
        withId shouldBe Some(WithId(entity, id))
      }
    }

    "return nothing by unknown id" in {
      whenReady {
        for {
          _ <- createEntity()
          withId <- dao.get((new ObjectId).toString)
        } yield withId
      }(_ shouldBe None)
    }

    "return nothing on bad id format" in {
      whenReady {
        for {
          _ <- createEntity()
          withId <- dao.get("42")
        } yield withId
      }(_ shouldBe None)
    }

    "fail on unknown filter" in {
      val unknownFilter = new Filter { }
      whenReady(dao.get(unknownFilter).failed) { e =>
        e shouldBe a [UnknownFilterException]
      }
    }

    "return entity with certain name" in {
      whenReady {
        for {
          _ <- createEntity()
          withId <- dao.get(NameIs(entity.name))
        } yield withId
      } { withId =>
        withId.map(_.entity) shouldBe Some(entity)
      }
    }

  }

  val entities = Seq(
    entity,
    SampleEntity("baz", List(1, 2)),
    SampleEntity("bar", List(2, 3))
  )

  "MongoEntityDao#listAll" should {

    "return all entities unsorted" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.listAll(TrueFilter)
        } yield withIds
      } { withIds =>
        withIds.map(_.entity) should contain allElementsOf entities
      }
    }

    "return all entities sorted by name ascending" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.listAll(TrueFilter, Some(SortBy(Name)))
        } yield withIds
      } { withIds =>
        withIds.map(_.entity) shouldBe entities.sortBy(_.name)
      }
    }

    "return all entities sorted by name descending" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.listAll(TrueFilter, Some(SortBy(Name, Descending)))
        } yield withIds
      } { withIds =>
        withIds.map(_.entity) shouldBe entities.sortBy(_.name)(implicitly[Ordering[String]].reverse)
      }
    }

    "throw if sorting not supported" in {
      whenReady {
        dao.listAll(TrueFilter, Some(SortBy(Unsupported, Descending))).failed
      } { e =>
        e shouldBe an [UnknownSortingFieldException]
      }
    }

    "return entities with certain ids" in {
      whenReady {
        for {
          ids <- createEntities(entities)
          withIds <- dao.listAll(IdIn(ids.take(2)))
        } yield withIds
      } { withIds =>
        withIds.size shouldBe 2
        withIds.map(_.entity) should contain allElementsOf entities.take(2)
      }
    }

    "return one entity with certain name" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.listAll(NameIs(entities.head.name))
        } yield withIds
      } { withIds =>
        withIds.map(_.entity) shouldBe Seq(entities.head)
      }
    }

    "return entities which contain certain data" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.listAll(ContainsData(2))
        } yield withIds
      } { withIds =>
        withIds.size shouldBe 2
        withIds.map(_.entity) should contain allElementsOf entities.filter(_.data.contains(2))
      }
    }

    "return entity which has certain name and contains certain data " in {
      val targetEntity = entities(2)
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.listAll(NameIs(targetEntity.name) && ContainsData(targetEntity.data.head))
        } yield withIds
      } { withIds =>
        withIds.map(_.entity) shouldBe Seq(targetEntity)
      }
    }

    "return entities which have certain name or contain certain data " in {
      val targetEntities = Seq(entities(0), entities(1))
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.listAll(NameIs(targetEntities(0).name) || ContainsData(targetEntities(1).data.head))
        } yield withIds
      } { withIds =>
        withIds.size shouldBe 2
        withIds.map(_.entity) should contain allElementsOf targetEntities
      }
    }

    "return entities which do not have certain name " in {
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.listAll(!NameIs(entity.name))
        } yield withIds
      } { withIds =>
        withIds.map(_.entity) should not contain entity
      }
    }

  }

  "MongoEntityDao#list" should {

    "return one page of two entities unsorted" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.list(TrueFilter, pageNumber = 1, pageSize = 2)
        } yield withIds
      } { withIds =>
        withIds.size shouldBe 2
      }
    }

    "return one page of two entities sorted by name ascending" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.list(TrueFilter, pageNumber = 1, pageSize = 2, Some(SortBy(Name)))
        } yield withIds
      } { withIds =>
        withIds.size shouldBe 2
        withIds.map(_.entity) shouldBe entities.sortBy(_.name).take(2)
      }
    }

    "return one page of two entities sorted by name descending" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.list(TrueFilter, pageNumber = 1, pageSize = 2, Some(SortBy(Name, Descending)))
        } yield withIds
      } { withIds =>
        withIds.size shouldBe 2
        withIds.map(_.entity) shouldBe entities.sortBy(_.name)(implicitly[Ordering[String]].reverse).take(2)
      }
    }

    "throw if sorting not supported" in {
      whenReady {
        dao.list(TrueFilter, pageNumber = 1, pageSize = 2, Some(SortBy(Unsupported, Descending))).failed
      } { e =>
        e shouldBe an [UnknownSortingFieldException]
      }
    }

    "return third entity on the second page of entities sorted by name ascending" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.list(TrueFilter, pageNumber = 2, pageSize = 2, Some(SortBy(Name)))
        } yield withIds
      } { withIds =>
        withIds.size shouldBe 1
        withIds.head.entity shouldBe entities.maxBy(_.name)
      }
    }

    "return third entity on the second page of entities sorted by name descending" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.list(TrueFilter, pageNumber = 2, pageSize = 2, Some(SortBy(Name, Descending)))
        } yield withIds
      } { withIds =>
        withIds.size shouldBe 1
        withIds.head.entity shouldBe entities.minBy(_.name)
      }
    }

    "return entities with certain ids" in {
      whenReady {
        for {
          ids <- createEntities(entities)
          withIds <- dao.list(IdIn(ids.take(2)), pageSize = entities.size, pageNumber = 1)
        } yield withIds
      } { withIds =>
        withIds.size shouldBe 2
        withIds.map(_.entity) should contain allElementsOf entities.take(2)
      }
    }

    "return one entity with certain name" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.list(NameIs(entities.head.name), pageSize = entities.size, pageNumber = 1)
        } yield withIds
      } { withIds =>
        withIds.map(_.entity) shouldBe Seq(entities.head)
      }
    }

    "return entities which contain certain data" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.list(ContainsData(2), pageSize = entities.size, pageNumber = 1)
        } yield withIds
      } { withIds =>
        withIds.size shouldBe 2
        withIds.map(_.entity) should contain allElementsOf entities.filter(_.data.contains(2))
      }
    }

    "return entity which has certain name and contains certain data " in {
      val targetEntity = entities(2)
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.list(
            NameIs(targetEntity.name) && ContainsData(targetEntity.data.head),
            pageSize = entities.size,
            pageNumber = 1
          )
        } yield withIds
      } { withIds =>
        withIds.map(_.entity) shouldBe Seq(targetEntity)
      }
    }

    "return entities which have certain name or contain certain data " in {
      val targetEntities = Seq(entities(0), entities(1))
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.list(
            NameIs(targetEntities(0).name) || ContainsData(targetEntities(1).data.head),
            pageSize = entities.size,
            pageNumber = 1
          )
        } yield withIds
      } { withIds =>
        withIds.size shouldBe 2
        withIds.map(_.entity) should contain allElementsOf targetEntities
      }
    }

    "return entities which do not have certain name " in {
      whenReady {
        for {
          _ <- createEntities(entities)
          withIds <- dao.list(!NameIs(entity.name), pageSize = entities.size, pageNumber = 1)
        } yield withIds
      } { withIds =>
        withIds.map(_.entity) should not contain entity
      }
    }

  }

  "MongoEntityDao#count" should {

    "return number of all entities" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          count <- dao.count(TrueFilter)
        } yield count
      }(_ shouldBe entities.size)
    }

    "return 0 when false filter is used" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          count <- dao.count(FalseFilter)
        } yield count
      }(_ shouldBe 0)
    }

    "return number of entities which contain certain data" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          count <- dao.count(ContainsData(2))
        } yield count
      }(_ shouldBe 2)
    }

  }

  val updatedName = "updatedName"
  val updatedData = List(60, 80)
  val entityUpdater: SampleEntity => SampleEntity = _.copy(name = updatedName, data = updatedData)

  "MongoEntityDao#update" should {

    "update certain entity" in {
      whenReady {
        for {
          ids <- createEntities(entities)
          id = ids.head
          updateResult <- dao.update(id, entityUpdater)
          currentWithId <- dao.get(id)
        } yield (updateResult, currentWithId)
      } { case (updateResult, currentWithId) =>
        updateResult should not be empty
        currentWithId.map(_.entity) shouldBe Some(entityUpdater(entity))
      }
    }

    "do nothing for unknown id" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          updateResult <- dao.update((new ObjectId).toString, _.copy(name = ""))
          currentWithIds <- dao.listAll(TrueFilter)
        } yield (updateResult, currentWithIds)
      } { case (updateResult, currentWithIds) =>
        updateResult shouldBe empty
        currentWithIds.map(_.entity) should contain allElementsOf entities
      }
    }

  }

  "MongoEntityDao#updateMany" should {

    "update all entities" in {
      whenReady {
        for {
          ids <- createEntities(entities)
          updatedCount <- dao.updateMany(TrueFilter, entityUpdater)
          currentWithIds <- dao.listAll(TrueFilter)
        } yield (updatedCount, currentWithIds)
      } { case (updatedCount, currentWithIds) =>
        updatedCount shouldBe entities.size
        currentWithIds.map(_.entity) should contain allElementsOf entities.map(entityUpdater)
      }
    }

    "update one entity with certain name" in {

      whenReady {
        for {
          _ <- createEntities(entities)
          updatedCount <- dao.updateMany(NameIs(entities.head.name), entityUpdater)
          currentWithIds <- dao.listAll(NameIs(updatedName))
        } yield (updatedCount, currentWithIds)
      } { case (updatedCount, currentWithIds) =>
        updatedCount shouldBe 1
        currentWithIds.map(_.entity) shouldBe Seq(entityUpdater(entities.head))
      }
    }

    "update entities which have certain name or contain certain data " in {
      val targetEntities = Seq(entities(0), entities(1))
      whenReady {
        for {
          _ <- createEntities(entities)
          updatedCount <- dao.updateMany(
            NameIs(targetEntities(0).name) || ContainsData(targetEntities(1).data.head),
            entityUpdater
          )
          currentWithIds <- dao.listAll(NameIs(updatedName) && ContainsData(updatedData.head))
        } yield (updatedCount, currentWithIds)
      } { case (updatedCount, currentWithIds) =>
        updatedCount shouldBe 2
        currentWithIds.map(_.entity) should contain allElementsOf targetEntities.map(entityUpdater)
      }
    }

    "do nothing when false filter is given" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          updatedCount <- dao.updateMany(FalseFilter, _.copy(name = ""))
          currentWithIds <- dao.listAll(TrueFilter)
        } yield (updatedCount, currentWithIds)
      } { case (updatedCount, currentWithIds) =>
        updatedCount shouldBe 0
        currentWithIds.map(_.entity) should contain allElementsOf entities
      }
    }

  }

  "MongoEntityDao#delete" should {

    "delete certain entity" in {
      whenReady {
        for {
          ids <- createEntities(entities)
          id = ids.head
          isDeleted <- dao.delete(id)
          currentWithIds <- dao.listAll(TrueFilter)
        } yield (isDeleted, currentWithIds)
      } { case (isDeleted, currentWithIds) =>
        isDeleted shouldBe true
        currentWithIds.map(_.entity) should not contain entities.head
      }
    }

    "do nothing for unknown id" in {
      whenReady {
        for {
          id <- createEntity()
          isDeleted <- dao.delete((new ObjectId).toString)
          currentWithId <- dao.get(id)
        } yield (isDeleted, currentWithId)
      } { case (isDeleted, currentWithId) =>
        isDeleted shouldBe false
        currentWithId.map(_.entity) shouldBe Some(entity)
      }
    }

  }

  "MongoEntityDao#deleteMany" should {

    "delete entity with certain name" in {
      whenReady {
        for {
          _ <- createEntities(entities)
          _ <- dao.deleteMany(NameIs(entities.head.name))
          currentWithIds <- dao.listAll(TrueFilter)
        } yield currentWithIds
      } { currentWithIds =>
        currentWithIds.map(_.entity) should not contain entities.head
      }
    }

    "do nothing when false filter is given" in {
      whenReady {
        for {
          id <- createEntity()
          _ <- dao.deleteMany(FalseFilter)
          currentWithId <- dao.get(id)
        } yield currentWithId
      } { currentWithId =>
        currentWithId.map(_.entity) shouldBe Some(entity)
      }
    }

  }

  "MongoEntityDao#listDistinctValues" should {

    "return list of distinct values" in {
      whenReady {
        for {
          _ <- createEntities(
            entities ++
              Seq(
                SampleEntity("baz", List(3, 4)),
                SampleEntity("foo", List(3, 2)),
                SampleEntity("bar", List(2, 1))
              )
          )
          names <- dao.listDistinctNames(TrueFilter)
        } yield names
      } { names =>
        names should contain theSameElementsAs Seq("foo", "bar", "baz")
      }
    }

  }

}
