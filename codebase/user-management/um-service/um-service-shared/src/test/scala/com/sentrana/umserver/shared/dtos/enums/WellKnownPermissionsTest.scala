package com.sentrana.umserver.shared.dtos.enums

import org.scalatest.{ MustMatchers, WordSpec }

import scala.collection.JavaConverters._
/**
 * Created by Alexander on 30.04.2016.
 */
class WellKnownPermissionsTest extends WordSpec with MustMatchers {
  "WellKnownPermissions.readPermissions" must {
    "return read only permissions" in {
      import WellKnownPermissions._
      WellKnownPermissions.getReadOnlyPermissions().asScala mustBe List(
        USERS_GET_DETAILS,
        USERS_SEARCH,
        GROUPS_GET_DETAILS,
        GROUPS_SEARCH,
        ORGS_GET_DETAILS,
        ORGS_SEARCH,
        APPS_GET_DETAILS,
        APPS_SEARCH,
        FILTERS_GET_DETAILS,
        FILTERS_SEARCH,
        SAML_PROVIDERS_GET_DETAILS,
        SAML_PROVIDERS_SEARCH
      )
    }
  }

}
