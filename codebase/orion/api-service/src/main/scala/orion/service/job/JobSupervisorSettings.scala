package orion.service.job

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config
import orion.common.utils.DurationExtensions._
import scala.concurrent.duration.FiniteDuration

class JobSupervisorSettings(config: Config) extends Extension {
  private val jobSupervisorConfig = config.getConfig("job-supervisor")
  val jobMasterStartTimeout: FiniteDuration = jobSupervisorConfig.getDuration("job-master-start-timeout").toScala
  val jobMasterHeartbeatTimeout: FiniteDuration = jobSupervisorConfig.getDuration("job-master-heartbeat-timeout").toScala
  val messagePublishingTimeout: FiniteDuration = jobSupervisorConfig.getDuration("message-publishing-timeout").toScala
  val messagePublishingRetries: Int = jobSupervisorConfig.getInt("message-publishing-retries")
}

object JobSupervisorSettings extends ExtensionId[JobSupervisorSettings] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): JobSupervisorSettings = new JobSupervisorSettings(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = JobSupervisorSettings

}
