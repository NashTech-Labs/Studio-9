package orion.testkit

import akka.testkit.{ DefaultTimeout, TestKitBase }
import org.scalatest.concurrent.{ AbstractPatienceConfiguration, PatienceConfiguration }
import org.scalatest.time.{ Millis, Seconds, Span }

trait AkkaPatienceConfiguration extends AbstractPatienceConfiguration { self: TestKitBase with DefaultTimeout with PatienceConfiguration =>

  private val defaultPatienceConfig: PatienceConfig =
    PatienceConfig(
      timeout  = scaled(Span(timeout.duration.toSeconds, Seconds)),
      interval = scaled(Span(150, Millis)) // Using default integration patience value here for now
    )

  implicit abstract override val patienceConfig: PatienceConfig = defaultPatienceConfig
}