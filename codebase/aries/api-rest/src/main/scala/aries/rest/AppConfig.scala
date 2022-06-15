package aries.rest

import aries.common.rest.BaseConfig
import aries.domain.rest.HttpContract

class AppConfig extends BaseConfig with HttpContract {

  override def appRootSectionName: String = "aries-service"
}
