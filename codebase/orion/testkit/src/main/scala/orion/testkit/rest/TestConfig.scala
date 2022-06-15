package orion.testkit.rest

import com.typesafe.config.Config
import orion.common.rest.BaseConfig

class TestConfig extends BaseConfig {

  override def appRootSectionName: String = "orion-service"

  override def config: Option[Config] = None
}
