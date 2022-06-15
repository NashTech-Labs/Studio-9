package pegasus.rest

import pegasus.common.rest.BaseConfig
import pegasus.domain.rest.HttpContract

class AppConfig extends BaseConfig with HttpContract {

  override def appRootSectionName: String = "pegasus-service"
}
