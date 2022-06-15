package cortex.rest.modules

import com.typesafe.config.{ Config, ConfigFactory }
import cortex.rest.AppConfig

trait SettingsModule {
  implicit val config = new AppConfig {
    override def config: Option[Config] = Some(ConfigFactory.load())
  }

}
