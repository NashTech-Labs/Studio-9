package com.sentrana.umserver.services

import com.sentrana.umserver.entities.UserEntity
import com.sentrana.umserver.shared.dtos.enums.{SortOrder, WellKnownPermissions}
import com.sentrana.umserver.{IntegrationTestUtils, OneAppWithMongo}
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._

/**
  * Created by Paul Lysak on 20.04.16.
  */
class UserGroupQueryServiceSpec extends PlaySpec with OneAppWithMongo {
  import IntegrationTestUtils.IT_PREFIX

  private lazy val itUtils = new IntegrationTestUtils()
  implicit private lazy val groupQueryService = app.injector.instanceOf(classOf[UserGroupQueryService])
  implicit private lazy val orgQueryService = app.injector.instanceOf(classOf[OrganizationQueryService])
  private lazy val userConverter = app.injector.instanceOf(classOf[UserConverter])

  private lazy val rootOrg = itUtils.createRootOrg()

  private lazy val g1 = itUtils.createTestGroup("g1", permissions = Set("DO_ONE_THING"))
  private lazy val g2 = itUtils.createTestGroup("g2", parentId = Option(g1.id), permissions = Set("DO_ANOTHER_THING", "DO_YET_ANOTHER_THING"))
  private lazy val g3 = itUtils.createTestGroup("g3", parentId = Option(g2.id), permissions = Set("DO_NOTHING", "DO_YET_ANOTHER_THING"))

  private lazy val g4 = itUtils.createTestGroup("g4", parentId = Option(g2.id), permissions = Set("DO_NOTHING", "DO_YET_ANOTHER_THING", "DO_MORE_THINGS"))

  private lazy val superGroup = itUtils.createTestGroup("superGroup", parentId = Option(g2.id), permissions = Set(WellKnownPermissions.SUPERUSER.toString))

  private lazy val user_g3 = itUtils.createTestUser(IT_PREFIX + "sampleUser_g3", groupIds = Set(g3.id))
  private lazy val user_g34 = itUtils.createTestUser(IT_PREFIX + "sampleUser_g34", groupIds = Set(g3.id, g4.id))

  private lazy val superUser = itUtils.createTestUser(IT_PREFIX + "sampleSuperUser", groupIds = Set(superGroup.id))

  private def groupPermissions(groupId: String): Set[String] = await(groupQueryService.getRecursivePermisions(rootOrg.id, groupId)).map(_.name)

  "UserGroupQueryService" must {
    "Return permissions for root group" in {
      rootOrg

      groupPermissions(g1.id) must be (Set("DO_ONE_THING"))
    }

    "Return permissions for child group together with permissions of the root group" in {
      groupPermissions(g2.id) must be (Set("DO_ONE_THING", "DO_ANOTHER_THING", "DO_YET_ANOTHER_THING"))
    }

    "Return permissions for 3-level child group" in {
      groupPermissions(g3.id) must be (Set("DO_ONE_THING", "DO_ANOTHER_THING", "DO_YET_ANOTHER_THING", "DO_NOTHING"))
    }

    "Return user permissions from single group" in {
      await(userConverter.toUserDetailDto(user_g3, Map.empty)).permissions must be (Set("DO_ONE_THING", "DO_ANOTHER_THING", "DO_YET_ANOTHER_THING", "DO_NOTHING"))
    }

    "Return user permissions from 2 groups" in {
      await(userConverter.toUserDetailDto(user_g34, Map.empty)).permissions must be (Set("DO_ONE_THING", "DO_ANOTHER_THING", "DO_YET_ANOTHER_THING", "DO_NOTHING", "DO_MORE_THINGS"))
    }

    "Check single-group user permissions" in {
      testPermissions(user_g3, Map("DO_ONE_THING" -> true,
        "DO_ANOTHER_THING" -> true,
        "DO_YET_ANOTHER_THING" -> true,
        "DO_NOTHING" -> true,
        "DO_MORE_THINGS" -> false,
        "DO_UNKNOWN_THINGS" -> false,
        "SUPERUSER" -> false
      ))
    }

    "Check 2-group user permissions" in {
      testPermissions(user_g34, Map("DO_ONE_THING" -> true,
        "DO_ANOTHER_THING" -> true,
        "DO_YET_ANOTHER_THING" -> true,
        "DO_NOTHING" -> true,
        "DO_MORE_THINGS" -> true,
        "DO_UNKNOWN_THINGS" -> false,
        "SUPERUSER" -> false
      ))
    }

    "Check superuser permissions" in {
      testPermissions(superUser, Map("DO_ONE_THING" -> true,
        "DO_ANOTHER_THING" -> true,
        "DO_YET_ANOTHER_THING" -> true,
        "DO_NOTHING" -> true,
        "DO_MORE_THINGS" -> true,
        "DO_UNKNOWN_THINGS" -> true,
        "SUPERUSER" -> true
      ))
    }

    "Return group hierarchy" in {
      val userGroupsHierarchy = await(groupQueryService.getUserGroupHierarchy(rootOrg.id, g4.id))
      userGroupsHierarchy.size mustBe 3
      userGroupsHierarchy.map(_.id) must contain allOf(g4.id, g2.id, g1.id)
    }

    "Find userGroups and sort" in {
      val expectedSize = 5
      val sortedUserGroupsInMemory = await(groupQueryService.find(rootOrg.id)).sortBy(u => (u.name, u.id))
      sortedUserGroupsInMemory.size mustBe expectedSize

      val sortedUserGroupsInMongo = await(groupQueryService.find(rootOrg.id,
        sortParams = Map("name" -> SortOrder.ASC,
        "id" -> SortOrder.ASC)))

      sortedUserGroupsInMongo.size mustBe expectedSize

      (0 until expectedSize).foreach { i =>
        sortedUserGroupsInMongo(i) mustBe sortedUserGroupsInMemory(i)
      }
    }
  }

  private def testPermissions(ue: UserEntity, expectedPermissions: Map[String, Boolean]): Unit = {
      val u = await(userConverter.toUserDetailDto(ue, Map.empty))
      val actualPermissions = expectedPermissions.foreach { case (k, v) =>
        k -> withClue(s"Permission $k:") {u.hasPermission(k) must be(v)}
      }
  }
}
