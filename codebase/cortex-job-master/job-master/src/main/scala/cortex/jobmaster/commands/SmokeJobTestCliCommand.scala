package cortex.jobmaster.commands

import cortex.common.logging.JMLoggerFactory
import cortex.io.S3Client
import cortex.jobmaster.jobs.example.SmokeJobRunner
import cortex.jobmaster.modules.SettingsModule
import cortex.rpc.S3TaskRPC

class SmokeJobTestCliCommand(
    mesosMaster: String,
    settings:    SettingsModule
)(implicit loggerFactory: JMLoggerFactory) extends CliCommand {
  import settings.baseS3Config
  import settings.jmConf

  private val s3client = new S3Client(baseS3Config.accessKey, baseS3Config.secretKey, baseS3Config.region)
  private val taskRPC = new S3TaskRPC(baseS3Config.baseBucket, settings.taskRpcPath, s3client)

  override def execute(): Unit = {
    new SmokeJobRunner().run(mesosMaster, taskRPC, jmConf.tasksVersion, jmConf.tasksRegistry)
  }
}
