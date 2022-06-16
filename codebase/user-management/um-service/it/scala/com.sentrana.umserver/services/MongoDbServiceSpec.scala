package com.sentrana.umserver.services

import java.time.ZonedDateTime
import java.util.UUID

import com.sentrana.umserver.OneAppWithMongo
import com.sentrana.umserver.entities.UserEntity
import com.sentrana.umserver.shared.dtos.enums.UserStatus
import org.mongodb.scala._
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.OptionValues
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._

/**
  * Created by Paul Lysak on 12.04.16.
  */
class MongoDbServiceSpec extends PlaySpec with OneAppWithMongo with OptionValues {
  import com.sentrana.umserver.entities.MongoFormats.userEntityMongoFormat

  private lazy val mongoDbService = app.injector.instanceOf(classOf[MongoDbService])

  private def generateId: String = UUID.randomUUID().toString

  private var usersAmount: Int = _

  val now = ZonedDateTime.now()
  val user1 = UserEntity(
      id            = generateId,
      username       = "uName",
      email          = "u@em.ail",
      password       = "TODO",
      firstName      = "firstName",
      lastName       = "lastName",
      status         = UserStatus.ACTIVE,
      created    = now,
      updated    = now,
      groupIds       = Set(),
      organizationId = "TODO",
      dataFilterInstances = Set()
    )

  "MongoDbService" must {

    "save an entity" in {
      await(mongoDbService.save(user1))
      ()
    }

    "read an entity" in {
      val actualUser = await(mongoDbService.get[UserEntity](user1.id)).value
      actualUser must be (user1)
    }

    "update an entity" in {
      val user2 = user1.copy(username = "updatedName")
      await(mongoDbService.update(user2, OrgScopeRoot))
      val actualUser = await(mongoDbService.get[UserEntity](user1.id)).value
      actualUser must not be(user1)
      actualUser must be(user2)
    }

    "delete an entity" in {
      await(mongoDbService.delete[UserEntity](user1.id, OrgScopeRoot))
      await(mongoDbService.get[UserEntity](user1.id)) must be (empty)
    }

    "search an entity" in {
      val u1 = user1.copy(id = generateId, username = "pig1", firstName = "Nufnuf")
      val u2 = user1.copy(id = generateId, username = "pig2", firstName = "Nifnif")
      val u3 = user1.copy(id = generateId, username = "pig3", firstName = "Nafnaf")
      await(mongoDbService.save(u1))
      await(mongoDbService.save(u2))
      await(mongoDbService.save(u3))
      val res = await(mongoDbService.find[UserEntity](Document()).toFuture()).toSet
      res mustBe(Set(u1, u2, u3))

      val res1 = await(mongoDbService.find[UserEntity](Document("firstName" -> "Nifnif")).toFuture()).toSet
      res1 mustBe(Set(u2))

      val res2 = await(mongoDbService.find[UserEntity](Document("username" -> "pig3")).toFuture()).toSet
      res2 mustBe(Set(u3))
    }

    "save additional records" in {
      (1 to 5).foreach { i =>
        val user = UserEntity(
          id            = generateId,
          username       = "uName"+i,
          email          = i+"u@em.ail",
          password       = "TODO",
          firstName      = "firstName",
          lastName       = "lastName",
          status         = UserStatus.ACTIVE,
          created    = now,
          updated    = now,
          groupIds       = Set(),
          organizationId = "TODO",
          dataFilterInstances = Set()
        )
        await(mongoDbService.save(user))
      }
    }

    "find with limit" in {
      val users = await(mongoDbService.find[UserEntity](Document(), offset = 0, limit = 2).toFuture())
      users.size mustBe 2
    }

    "find and sort results" in {
      val usersSortedInMongoDB = await(mongoDbService.find[UserEntity](Document(), sort = Option(Document("{\"username\":1}"))).toFuture())
      val usersSortedInMemory = await(mongoDbService.find[UserEntity](Document()).toFuture()).sortBy(_.username)
      usersSortedInMongoDB.size mustBe 8
      usersSortedInMemory.size mustBe 8

      (0 until usersSortedInMongoDB.size).foreach { i =>
        usersSortedInMongoDB(i) mustBe usersSortedInMemory(i)
      }
    }

    "count amount of records in collection" in {
      val users = await(mongoDbService.find[UserEntity](Document()).toFuture())
      users.size must not be 0
      usersAmount = users.size

      val count = await(mongoDbService.count[UserEntity](Document()))
      usersAmount mustBe count
    }

    "find with offset" in {
      val offsetAmount = 2
      val users = await(mongoDbService.find[UserEntity](Document(), offset = offsetAmount).toFuture())
      users.size mustBe (usersAmount - offsetAmount)
    }

    "aggregate pipeline" in {
      val result = await(mongoDbService.aggregate[UserEntity]("users", Seq(
        Document("{ $match: { \"username\": \"pig1\" } }")
      )))
      result.size mustBe 1
      result(0).username mustBe "pig1"
    }
  }
}
