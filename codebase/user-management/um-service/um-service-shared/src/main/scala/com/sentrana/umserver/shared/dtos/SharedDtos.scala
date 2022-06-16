package com.sentrana.umserver.shared.dtos

import java.time.ZonedDateTime

import com.sentrana.umserver.shared.dtos.enums._

case class User(
    id: String,
    username: String,
    email: String,
    firstName: String,
    lastName: String,
    status: UserStatus,
    dataFilterInstances: Set[DataFilterInstance],
    userGroupIds: Set[String],
    permissions: Set[String],
    organizationId: String,
    fromRootOrg: Boolean,
    externalId: Option[String] = None,
    created: ZonedDateTime = ZonedDateTime.now(),
    updated: ZonedDateTime = ZonedDateTime.now(),
    organization: Option[Organization] = None
) extends WithId {
  def active = status == UserStatus.ACTIVE

  def deleted = status == UserStatus.DELETED

  lazy val superuser: Boolean = permissions.contains(WellKnownPermissions.SUPERUSER.toString)

  def hasPermission(permission: String): Boolean = {
    superuser || permissions.contains(permission)
  }
}

case class UserGroup(
  id: String,
  organizationId: String,
  parentGroupId: Option[String],
  name: String,
  desc: Option[String],
  grantsPermissions: Set[Permission],
  forChildOrgs: Boolean = false,
  dataFilterInstances: Set[DataFilterInstance] = Set(),
  created: ZonedDateTime = ZonedDateTime.now(),
  updated: ZonedDateTime = ZonedDateTime.now()
) extends WithId

case class Permission(name: String)

/**
 * snake_case for the names is choosen in order to be compatible with OAuth 2 spec
 *
 * @param access_token
 * @param token_type only "bearer" at the moment
 * @param expires_in seconds
 */
case class TokenResponse(access_token: String, expires_in: Int, token_type: String = "bearer")

case class Organization(
    id: String,
    name: String,
    desc: Option[String] = None,
    parentOrganizationId: Option[String] = None,
    status: OrganizationStatus = OrganizationStatus.ACTIVE,
    applicationIds: Set[String] = Set(),
    dataFilterInstances: Set[DataFilterInstance] = Set(),
    created: ZonedDateTime = ZonedDateTime.now(),
    updated: ZonedDateTime = ZonedDateTime.now(),
    signUpEnabled: Boolean = false,
    signUpGroupIds: Set[String] = Set()
) extends WithId {
  def isActive = status == OrganizationStatus.ACTIVE
}

case class OrgParents(
  parentIds: Seq[String]
)

case class ApplicationInfo(
  id: String,
  name: String,
  desc: Option[String],
  url: Option[String],
  passwordResetUrl: Option[String] = None,
  emailConfirmationUrl: Option[String] = None,
  created: ZonedDateTime = ZonedDateTime.now(),
  updated: ZonedDateTime = ZonedDateTime.now()
)

object ApplicationInfo {
  val DUMMY_ID = "DUMMY"
}

case class ClientSecretInfo(clientId: String, clientSecret: String)

case class SeqPartContainer[T](data: Seq[T], offset: Long, total: Long)

case class DataFilterInfo(
  id: String,
  fieldName: String,
  fieldDesc: String,
  valuesQuerySettings: Option[DataFilterInfoSettings],
  displayName: Option[String] = None,
  showValueOnly: Boolean = false
) extends WithId

case class DataFilterInfoSettings(
  validValuesQuery: String,
  dbName: String,
  dbType: DBType = DBType.MONGO,
  dataType: String,
  collectionName: Option[String] = None
)

case class DataFilterInstance(
  dataFilterId: String,
  operator: FilterOperator,
  values: Set[String]
)

case class UserSignUpRequest(
  username: String,
  email: String,
  password: String,
  firstName: String,
  lastName: String,
  requireEmailConfirmation: Option[Boolean] = None
)

case class ReSendActivationLinkRequest(
  email: String,
  appId: Option[String] = None
)

case class UserPasswordHistory(
  id: String,
  userId: String,
  password: String,
  created: ZonedDateTime = ZonedDateTime.now()
) extends WithId

case class UpdatePasswordRequest(oldPassword: String, newPassword: String)

case class PasswordResetRequest(
  email: String,
  appId: Option[String] = None
)

case class UpdateForgottenPasswordRequest(email: String, secretCode: String, newPassword: String)
