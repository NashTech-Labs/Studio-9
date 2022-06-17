package taurus.rest

import taurus.domain.rest.HttpContract
import taurus.common.rest.BaseConfig

class AppConfig extends BaseConfig with HttpContract {

  override def appRootSectionName: String = "taurus-service"
}
