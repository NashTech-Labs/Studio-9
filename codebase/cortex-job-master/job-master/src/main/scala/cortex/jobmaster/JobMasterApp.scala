package cortex.jobmaster

import cortex.common.logging.JMLoggerFactoryImpl
import cortex.jobmaster.commands._
import cortex.jobmaster.modules.SettingsModule

object JobMasterApp extends CliParamsParser {

  private val settings = SettingsModule()
  override def serviceName: String = settings.jmConf.serviceName
  override def tasksVersion: String = settings.jmConf.tasksVersion

  def main(args: Array[String]): Unit = {
    parser.parse(args, JobMasterParams()) foreach { params =>

      implicit val loggerFactory: JMLoggerFactoryImpl = new JMLoggerFactoryImpl(params.jobId)
      val log = loggerFactory.getLogger("main")

      val command: CliCommand = params.mode match {
        case "version" =>
          new VersionCliCommand(settings.jmConf.tasksVersion)

        case "service" =>
          new ServiceCliCommand(
            jobId       = params.jobId,
            mesosMaster = params.mesosMaster,
            settings    = settings
          )

        case "test" =>
          params.jobType match {
            case "smoke" =>
              log.info("Starting simple smoke job")
              new SmokeJobTestCliCommand(
                mesosMaster = params.mesosMaster,
                settings    = settings
              )

            case "gpu" =>
              log.info("Starting simple gpu job")
              new GpuJobTestCliCommand(
                mesosMaster = params.mesosMaster,
                settings    = settings
              )

            case unknownJobType =>
              log.error(s"Job $unknownJobType is not found")
              EmptyCliCommand
          }

        case "job" =>
          new JobCliCommand(
            jobId              = params.jobId,
            filePath           = params.filePath,
            dockerImageVersion = params.version,
            settings           = settings
          )
      }

      command.execute()
    }
  }
}
