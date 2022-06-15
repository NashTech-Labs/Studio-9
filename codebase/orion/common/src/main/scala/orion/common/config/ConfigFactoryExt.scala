package orion.common.config

import orion.common._
/**
 * Convenient extensions to typesafe ConfigFactory behavior
 */
object ConfigFactoryExt {
  /**
   * Configures the typesafe config library so that it reads an environment
   * specific configuration file instead of the default application.conf.
   *
   * The prefix of the file to load is taken from the value of the 'env' system
   * property.  For example, to read production.conf rather that application.conf,
   * specify -DCORTEX_ENV=production in the command starting the server as in:
   *
   * java -jar server.jar -DCORTEX_ENV=production
   * Or setup the system environmental variable
   * EXPORT CORTEX_ENV=production
   */
  def enableEnvOverride(): Unit = {
    val env = System.getProperty("CORTEX_ENV")
    if (env.isDefined) {
      System.setProperty("config.resource", env + ".conf")
    } else {
      val env = System.getenv("CORTEX_ENV")
      if (env.isDefined) {
        System.setProperty("config.resource", env + ".conf")
      }
    }
  }
}
