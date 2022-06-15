package cortex.common.logging

trait JMLoggerFactory {

  def getLogger(loggerName: String): JMLogger
}

class JMLoggerFactoryImpl(jobId: String) extends JMLoggerFactory {

  override def getLogger(loggerName: String): JMLogger = {
    new JMLogger(loggerName, jobId)
  }
}
