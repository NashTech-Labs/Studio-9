package cortex.testkit

trait OptionHelpers {
  implicit class OptionExtension[A](opt: Option[A]) {
    def getMandatory: A = {
      opt.getOrElse(throw new IllegalStateException("option is empty"))
    }
  }
}

object OptionHelpers extends OptionHelpers
