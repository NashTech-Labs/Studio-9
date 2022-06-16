package com.sentrana.umserver.entities

import com.sentrana.umserver.dtos.SamlProvider
import com.sentrana.umserver.shared.dtos._

/**
 * Created by Paul Lysak on 19.04.16.
 */
object MongoFormats {
  import com.sentrana.umserver.JsonFormats._

  implicit val userGroupMongoFormat = new MongoEntityDefaultFormat[UserGroup]("userGroups")

  implicit val organizationMongoFormat = new MongoEntityDefaultFormat[Organization]("organizations")

  implicit val userEntityMongoFormat = new MongoEntityDefaultFormat[UserEntity]("users")

  implicit val userLoginRecordFormat = new MongoEntityDefaultFormat[UserLoginRecord]("userLoginRecords")

  implicit val applicationInfoEntityFormat = new MongoEntityDefaultFormat[ApplicationInfoEntity]("applications")

  implicit val dataFilterInfoMongoFormat = new MongoEntityDefaultFormat[DataFilterInfo]("dataFilters")

  implicit val samlProviderMongoFormat = new MongoEntityDefaultFormat[SamlProvider]("samlProviders")

  implicit val userPasswordHistoryMongoFormat = new MongoEntityDefaultFormat[UserPasswordHistory]("userPasswords")

  implicit val passwordResetMongoFormat = new MongoEntityDefaultFormat[PasswordReset]("passwordReset")
}
