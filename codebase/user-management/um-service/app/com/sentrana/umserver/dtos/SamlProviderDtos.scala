package com.sentrana.umserver.dtos

import java.time.ZonedDateTime

import com.sentrana.umserver.shared.dtos.WithId
import play.api.libs.json.Json

/**
 * Created by Paul Lysak on 18.05.16.
 *
 * @param serviceProvider our name for SAML provider
 */
case class SamlProvider(
  id:                    String,
  name:                  String,
  desc:                  Option[String],
  url:                   String,
  idProviderCertificate: String,
  serviceProvider:       String,
  organizationId:        String,
  defaultGroupIds:       Set[String]    = Set(),
  created:               ZonedDateTime  = ZonedDateTime.now(),
  updated:               ZonedDateTime  = ZonedDateTime.now()
) extends WithId
//TODO: defaultGroupId param

case class CreateSamlProviderRequest(
  name:                  String,
  desc:                  Option[String],
  url:                   String,
  idProviderCertificate: String,
  serviceProvider:       String,
  organizationId:        String,
  defaultGroupIds:       Option[Set[String]] = None
)

object CreateSamlProviderRequest {
  implicit val reads = Json.reads[CreateSamlProviderRequest]
}

case class UpdateSamlProviderRequest(
  name:                  Option[String]      = None,
  desc:                  Option[String]      = None,
  url:                   Option[String]      = None,
  idProviderCertificate: Option[String]      = None,
  serviceProvider:       Option[String]      = None,
  defaultGroupIds:       Option[Set[String]] = None
)

object UpdateSamlProviderRequest {
  implicit val reads = Json.reads[UpdateSamlProviderRequest]
}
