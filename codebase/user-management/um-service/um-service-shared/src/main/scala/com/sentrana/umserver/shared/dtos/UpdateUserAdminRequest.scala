package com.sentrana.umserver.shared.dtos

/**
 * Created by Paul Lysak on 01.07.16.
 */
case class UpdateUserAdminRequest(
  username: Option[String] = None,
  email: Option[String] = None,
  password: Option[String] = None,
  firstName: Option[String] = None,
  lastName: Option[String] = None,
  groupIds: Option[Set[String]] = None,
  dataFilterInstances: Option[Set[DataFilterInstance]] = None,
  externalId: Option[String] = None
)

