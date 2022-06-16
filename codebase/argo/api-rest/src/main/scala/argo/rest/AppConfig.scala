package argo.rest

import argo.common.rest.BaseConfig
import argo.domain.rest.HttpContract

class AppConfig extends BaseConfig with HttpContract {

  override def appRootSectionName: String = "argo-service"
}
