package cortex.jobmaster.commands

trait CliCommand {
  def execute(): Unit
}
