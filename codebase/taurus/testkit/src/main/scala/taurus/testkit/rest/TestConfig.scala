package taurus.testkit.rest

import taurus.common.rest.BaseConfig

class TestConfig extends BaseConfig {

  override def appRootSectionName: String = "cortex-service"
}
