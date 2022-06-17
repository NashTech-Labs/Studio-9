package com.sentrana.umserver

import java.time.ZonedDateTime

import com.sentrana.umserver.dtos._
import com.sentrana.umserver.entities.{PasswordReset, ApplicationInfoEntity, UserEntity}
import com.sentrana.umserver.services._
import com.sentrana.umserver.shared.BaseSecuredController
import com.sentrana.umserver.shared.dtos._
import com.sentrana.umserver.shared.dtos.enums.{DBType, OrganizationStatus, PasswordResetStatus}
import play.api.{Application, Configuration}
import play.api.test.Helpers._

import scala.concurrent.Future

/**
  * Created by Paul Lysak on 14.04.16.
  */
class IntegrationTestUtils(implicit app: Application) {
  import IntegrationTestUtils._

  private lazy val userService = app.injector.instanceOf(classOf[UserService])
  private lazy val groupService = app.injector.instanceOf(classOf[UserGroupService])
  private lazy val orgsService = app.injector.instanceOf(classOf[OrganizationService])
  private lazy val orgQueryService = app.injector.instanceOf(classOf[OrganizationQueryService])
  private lazy val mongoDbService = app.injector.instanceOf(classOf[MongoDbService])
  private lazy val applicationInfoService = app.injector.instanceOf(classOf[ApplicationInfoService])
  private lazy val dataFilterInfoService = app.injector.instanceOf(classOf[DataFilterInfoService])

  private lazy val DEFAULT_ORG = orgQueryService.rootOrgId


  def createTestUser(userName: String, password: String = DEFAULT_PWD, orgId: String = DEFAULT_ORG, groupIds: Set[String] = Set(), dataFilterInstances: Set[DataFilterInstance] = Set(), email: Option[String] = None): UserEntity = {
    await(userService.create(orgId, CreateUserRequest(
      username = userName,
      email = email.getOrElse(userName + "@some.server.com"),
      password = password,
      firstName = "John",
      lastName = "Doe",
      groupIds = groupIds,
      dataFilterInstances = Option(dataFilterInstances)
    )))
  }

  def createTestGroup(name: String, parentId: Option[String] = None, orgId: String = DEFAULT_ORG, permissions: Set[String] = Set(), forChildOrgs: Boolean = false, dataFilterInstances:  Set[DataFilterInstance] = Set()): UserGroup = {
    await(groupService.create(orgId, CreateUserGroupRequest(
        parentGroupId = parentId,
        name = name,
        desc = None,
        grantsPermissions = permissions.map(Permission.apply),
        forChildOrgs = forChildOrgs,
        dataFilterInstances = Option(dataFilterInstances))
      ))
  }

  def createRootOrg(id: String = "12345"): Organization = {
    import com.sentrana.umserver.entities.MongoFormats.organizationMongoFormat

    val org = Organization(id = id,
      name = "Root organization")
    await(mongoDbService.save(org))
  }

  def createTestOrg(name: String, parentId: String, dataFilterInstance: Set[DataFilterInstance] = Set()): Organization = {
    await(orgsService.create(CreateOrganizationRequest(name = name, parentOrganizationId = parentId, dataFilterInstances = Some(dataFilterInstance))))
  }

  def changeOrgStatus(orgId: String, status: OrganizationStatus): Organization = {
    import com.sentrana.umserver.entities.MongoFormats._

    val org = await(orgQueryService.getMandatory(orgId))
    await(mongoDbService.update(org.copy(status = status), OrgScopeRoot))
  }

  def createTestApplicationInfo(
    name: String,
    description: Option[String] = None,
    url: Option[String] = None,
    emailConfirmationUrl: Option[String] = None
  ): ApplicationInfoEntity = {
    await(applicationInfoService.create(CreateApplicationInfoRequest(
      name = name,
      desc = description,
      url = url,
      emailConfirmationUrl = emailConfirmationUrl
    )))
  }

  def createTestDataFilterInfo(fieldName: String,
                               validValuesQuery: String = "",
                               dbName: String = "",
                               dbType: DBType = DBType.MONGO,
                               collectionName: Option[String] = None): DataFilterInfo = {
    await(dataFilterInfoService.create(CreateDataFilterInfoRequest(
      fieldName,
      fieldDesc = "testDesc",
      valuesQuerySettings = Option(DataFilterInfoSettings(
        validValuesQuery = validValuesQuery,
        dbName = dbName,
        dbType = dbType,
        dataType = "",
        collectionName = collectionName
      )),
      displayName = None,
      showValueOnly = false))
    )
  }

  def createTestPasswordReset(userId: String, email: String, secretCode: String, status: PasswordResetStatus, created: ZonedDateTime = ZonedDateTime.now()): PasswordReset = {
    import com.sentrana.umserver.entities.MongoFormats.passwordResetMongoFormat
    await(mongoDbService.save[PasswordReset](PasswordReset(
      id = mongoDbService.generateId,
      secretCode = secretCode,
      status = status,
      userId = userId,
      email = email,
      created = created
    )))
  }
}

object IntegrationTestUtils {
  val IT_PREFIX = "integrationTest_"

  val DEFAULT_PWD = "knockKnock"
}
