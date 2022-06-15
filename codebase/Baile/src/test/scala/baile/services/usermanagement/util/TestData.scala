package baile.services.usermanagement.util

import java.time.ZonedDateTime
import java.util.UUID

import baile.domain.usermanagement.{ Permission, RegularUser, UserStatus, Role }


object TestData {

  val DateAndTime = ZonedDateTime.now
  val SampleUser: RegularUser = RegularUser(
    UUID.fromString("e4575008-a1b0-4e22-9103-633bd1f1b437"),
    "john.doe",
    "jd@example.com",
    "John",
    "Doe",
    UserStatus.Active,
    DateAndTime.toInstant,
    DateAndTime.toInstant,
    Seq(),
    Role.User
  )

  val SampleAdmin: RegularUser = RegularUser(
    UUID.fromString("e4475008-a1b0-4e22-9103-633bd1f1b437"),
    "john.doe",
    "jd@example.com",
    "John",
    "Doe",
    UserStatus.Active,
    DateAndTime.toInstant,
    DateAndTime.toInstant,
    Seq(Permission.SuperUser),
    Role.Admin
  )

}
