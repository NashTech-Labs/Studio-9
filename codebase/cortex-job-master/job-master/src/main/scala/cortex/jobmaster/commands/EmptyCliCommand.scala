package cortex.jobmaster.commands

object EmptyCliCommand extends CliCommand {
  override def execute(): Unit = ()
}
