package orion.rest.modules

import java.net.InetAddress

import com.typesafe.config.{ Config, ConfigFactory }
import orion.common.rest.BaseConfig

trait SettingsModule {

  private val localAddress: String = InetAddress.getLocalHost.getHostAddress

  implicit val config = new BaseConfig {
    override val appRootSectionName: String = "orion-service"

    override def config: Option[Config] = {
      Some(ConfigFactory.parseString("akka.remote.netty.tcp.bind-hostname=" + localAddress)
        .withFallback(ConfigFactory.load()))
    }
  }
}
