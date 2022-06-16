package com.sentrana.umserver

import com.sentrana.umserver.services.AuthenticationService
import com.sentrana.umserver.shared.dtos.enums.WellKnownPermissions
import play.api.test.FakeApplication

/**
  * Created by Paul Lysak on 20.04.16.
  */
trait WithAdminUser {
  import IntegrationTestUtils.IT_PREFIX

  protected implicit def app: FakeApplication

  protected lazy val itUtils = new IntegrationTestUtils()
  protected lazy val authService = app.injector.instanceOf(classOf[AuthenticationService])

  protected lazy val rootOrg = itUtils.createRootOrg()
  protected lazy val adminGroup = itUtils.createTestGroup(IT_PREFIX + "adminGroup",
    permissions = Set(WellKnownPermissions.SUPERUSER.toString), forChildOrgs = true)
  protected lazy val adminUser  = itUtils.createTestUser(IT_PREFIX + "sampleAdmin1", groupIds = Set(adminGroup.id), orgId = rootOrg.id)
  protected lazy val adminToken = authService.issueToken(adminUser)._1

  protected lazy val activationAndDeactivationGroup = itUtils.createTestGroup(
    IT_PREFIX + "testGroup",
    permissions = Set(WellKnownPermissions.USERS_ACTIVATE.toString, WellKnownPermissions.USERS_DEACTIVATE.toString),
    forChildOrgs = true
  )
  protected lazy val userWithDeactivatePermission =
    itUtils.createTestUser(
      IT_PREFIX + "sampleAdmin2",
      groupIds = Set(activationAndDeactivationGroup.id),
      orgId = rootOrg.id,
      email = Some("admin.joe.@some.server.com")
    )
  protected lazy val userWithDeactivatePermissionToken = authService.issueToken(userWithDeactivatePermission)._1

  protected lazy val userWithNoPermissions = itUtils.createTestUser(
    IT_PREFIX + "userWithNoPermission",
    groupIds = Set.empty,
    orgId = rootOrg.id,
    email = Some("average.joe.@some.server.com")
  )

  protected lazy val userWithNoPermissionsToken = authService.issueToken(userWithNoPermissions)._1

}
