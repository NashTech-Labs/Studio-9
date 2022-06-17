package com.sentrana.umserver.dtos

import com.sentrana.umserver.shared.dtos.DataFilterInstance
import play.api.libs.json.Json

/**
 *
 * @param name
 * @param parentOrganizationId - parent organization is mandatory because root org can't be created from HTTP request
 */
case class CreateOrganizationRequest(
  name:                 String,
  parentOrganizationId: String,
  desc:                 Option[String]                  = None,
  applicationIds:       Option[Set[String]]             = None,
  dataFilterInstances:  Option[Set[DataFilterInstance]] = None,
  signUpEnabled:        Option[Boolean]                 = None,
  signUpGroupIds:       Option[Set[String]]             = None
)

object CreateOrganizationRequest {
  import com.sentrana.umserver.JsonFormats._
  implicit val reads = Json.reads[CreateOrganizationRequest]
}

case class UpdateOrganizationRequest(
  name:                Option[String],
  desc:                Option[String],
  applicationIds:      Option[Set[String]]             = None,
  dataFilterInstances: Option[Set[DataFilterInstance]] = None,
  signUpEnabled:       Option[Boolean]                 = None,
  signUpGroupIds:      Option[Set[String]]             = None
)

object UpdateOrganizationRequest {
  import com.sentrana.umserver.JsonFormats._
  implicit val reads = Json.reads[UpdateOrganizationRequest]
}

