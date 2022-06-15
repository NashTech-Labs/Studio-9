package taurus.rest.modules

import com.typesafe.config.{ Config, ConfigFactory }
import taurus.rest.AppConfig

trait SettingsModule {
  implicit val config = new AppConfig {
    override def config: Option[Config] = Some(ConfigFactory.load())
  }
}
