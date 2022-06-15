package cortex.rest

import cortex.common.rest.BaseConfig
import cortex.domain.rest.HttpContract

class AppConfig extends BaseConfig with HttpContract {

  override def appRootSectionName: String = "cortex-service"
}
