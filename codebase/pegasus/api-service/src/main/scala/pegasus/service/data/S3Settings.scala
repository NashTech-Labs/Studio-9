package pegasus.service.data

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.amazonaws.regions.Regions
import com.typesafe.config.Config

class S3Settings(config: Config) extends Extension {
  private val s3Config = config.getConfig("aws.s3")

  val region = Regions.fromName(s3Config.getString("region"))
  val accessKeyId = s3Config.getString("credentials.access-key-id")
  val secretAccessKey = s3Config.getString("credentials.secret-access-key")

}

object S3Settings extends ExtensionId[S3Settings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): S3Settings = new S3Settings(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = S3Settings
}