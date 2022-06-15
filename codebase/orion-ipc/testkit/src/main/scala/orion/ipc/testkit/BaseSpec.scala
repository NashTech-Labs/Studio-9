package orion.ipc.testkit

import org.scalatest._

trait BaseSpec extends WordSpecLike with Matchers with OptionValues with Inside with Inspectors {}
