package cortex.testkit

import cortex.common.logging.{ JMLoggerFactory, JMLoggerFactoryImpl }

trait WithLogging {

  implicit val loggerFactory: JMLoggerFactory = new JMLoggerFactoryImpl("test_job")

}
