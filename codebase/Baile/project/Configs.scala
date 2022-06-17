import sbt._

object Configs {

  // Because default IntegrationTest config extends Runtime, not Test,
  // which prevents from sharing sources between it and regular tests folders.
  lazy val BaileIntegrationTest = config("it") extend Test

}
