package argo.testkit.rest

import argo.common.rest.BaseConfig

class TestConfig extends BaseConfig {

  override def appRootSectionName: String = "argo-service"
}
