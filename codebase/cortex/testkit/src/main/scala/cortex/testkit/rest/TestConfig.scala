package cortex.testkit.rest

import cortex.common.rest.BaseConfig

class TestConfig extends BaseConfig {

  override def appRootSectionName: String = "cortex-service"
}
