import sbt._

object Configs {
  val IntegrationTest = config("it") extend Test
  val EndToEndTest = config("e2e") extend Test
  val BenchmarkTest = config("bench") extend Test
  val all = Seq(IntegrationTest, EndToEndTest, BenchmarkTest)
}