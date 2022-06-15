import Configs.BenchmarkTest
import Configs.EndToEndTest
import Configs.IntegrationTest
import Tasks._
import sbt.Keys._
import sbt._

object Testing {

  private lazy val itSettings =
    inConfig(IntegrationTest)(Defaults.testSettings) ++
      Seq(
        fork in IntegrationTest := true,
        parallelExecution in IntegrationTest := false,
        scalaSource in IntegrationTest := baseDirectory.value / "src/it/scala")

  private lazy val e2eSettings =
    inConfig(EndToEndTest)(Defaults.testSettings) ++
      Seq(
        fork in EndToEndTest := true,
        parallelExecution in EndToEndTest := false,
        scalaSource in EndToEndTest := baseDirectory.value / "src/e2e/scala")

  private lazy val benchmarkSettings =
    inConfig(BenchmarkTest)(Defaults.testSettings) ++
      Seq(
        fork in BenchmarkTest := true,
        parallelExecution in BenchmarkTest := false,
        logBuffered := false,
        scalaSource in BenchmarkTest := baseDirectory.value / "src/bench/scala")

  lazy val settings = itSettings ++ e2eSettings ++ benchmarkSettings ++ Seq(
    testAll := (test in BenchmarkTest)
      .dependsOn((test in EndToEndTest)
        .dependsOn((test in IntegrationTest)
          .dependsOn(test in Test))).value
  ) ++ Seq(
    testForBuild := (test in IntegrationTest).dependsOn(test in Test).value
  )
}