package com.sentrana.umserver.services

import com.sentrana.umserver.dtos.UpdateUserGroupRequest
import com.sentrana.umserver.shared.dtos.enums.WellKnownPermissions
import com.sentrana.umserver.{WithAdminUser, IntegrationTestUtils, OneAppWithMongo}
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._

/**
  * Created by Paul Lysak on 17.06.16.
  */
class UserGroupServiceSpec extends PlaySpec with OneAppWithMongo with WithAdminUser {

  implicit private lazy val groupService = app.injector.instanceOf(classOf[UserGroupService])

  implicit private lazy val groupQueryService = app.injector.instanceOf(classOf[UserGroupQueryService])

  private lazy val org1 = itUtils.createTestOrg("org1", rootOrg.id)
  private lazy val org2 = itUtils.createTestOrg("org1", rootOrg.id)

//  private lazy val superGroup = itUtils.createTestGroup("superGroup",
//    permissions = Set(WellKnownPermissions.SUPERUSER.toString),
//    forChildOrgs = true)
  private lazy val rg1 = itUtils.createTestGroup("rg1")
  private lazy val rg2s = itUtils.createTestGroup("rg2s", forChildOrgs = true)
  private lazy val o1g1 = itUtils.createTestGroup("o1g1", orgId = org1.id)
  private lazy val o1g2s = itUtils.createTestGroup("o1g2s", orgId = org1.id, forChildOrgs = true)
  private lazy val o2g1 = itUtils.createTestGroup("o2g1", orgId = org2.id)

//  private lazy val o1admin1 = itUtils.createTestUser("o1admin1", orgId = org1.id, groupIds = Set(superGroup.id))
//  private lazy val o2admin1 = itUtils.createTestUser("o2admin1", orgId = org2.id, groupIds = Set(superGroup.id))


  "UserGroupService" must {
    "update root org group in root org scope" in {
      await(groupService.update(rootOrg.id, rg1.id, UpdateUserGroupRequest(desc = Option("new desc"))))
      ()
    }

    "update regular org group in root org scope" in {
      await(groupService.update(rootOrg.id, o1g1.id, UpdateUserGroupRequest(desc = Option("new desc"))))
      ()
    }

    "update regular org group n same regular org scope" in {
      await(groupService.update(org1.id, o1g1.id, UpdateUserGroupRequest(desc = Option("new desc"))))
      ()
    }

    "not update regular org group in other regular org scope" in {
      intercept[Exception] {
        await(groupService.update(org2.id, o1g1.id, UpdateUserGroupRequest(desc = Option("new desc"))))
      }
      ()
    }

    "not update regular org group in other regular org scope even if group has forChildOrgs = true" in {
      intercept[Exception] {
        await(groupService.update(org2.id, o1g2s.id, UpdateUserGroupRequest(desc = Option("new desc"))))
      }
      ()
    }

    "not update root org group in regular org scope" in {
      intercept[Exception] {
        await(groupService.update(org1.id, rg1.id, UpdateUserGroupRequest(desc = Option("new desc"))))
      }
      ()
    }

    "not update root org group in regular org scope even if group has forChildOrgs = true" in {
      intercept[Exception] {
        await(groupService.update(org1.id, rg2s.id, UpdateUserGroupRequest(desc = Option("new desc"))))
      }
      ()
    }
  }
}
