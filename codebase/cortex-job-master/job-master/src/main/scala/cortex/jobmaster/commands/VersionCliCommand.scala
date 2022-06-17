package cortex.jobmaster.commands

class VersionCliCommand(tasksVersion: String) extends CliCommand {
  override def execute(): Unit = {
    // scalastyle:off
    println(tasksVersion)
    // scalastyle:on
  }
}
