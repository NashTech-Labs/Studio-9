import Versions.JDKVersion
import sbt.Test
import Settings.dockerSettings

lazy val SQLServer = Project("sql-server", file("."))
  .settings(
    name := "sql-server",
    organization := "deepcortex",
    description := "Service for handling SQL requests",
    version := "0.1",
    homepage := Some(url("https://github.com/deepcortex/sql-server")),
    coverageMinimum := 75,
    coverageFailOnMinimum := false,
    Compile / mainClass := Some("sqlserver.HttpServerApp"),
    scalaVersion := Versions.ScalaVersion,
    scalacOptions ++= Seq(
      s"-target:jvm-$JDKVersion",
      "-encoding", "UTF-8",
      "-Xfatal-warnings",
      "-Xlint:-unused,_",
      "-Ywarn-dead-code",
      "-Ywarn-unused:imports", // Warn if an import selector is not referenced
      "-Ywarn-unused:locals", // Warn if a local definition is unused
      "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused
      "-Ywarn-unused:privates", // Warn if a private member is unused
      "-deprecation",
      "-feature"
    ),
    Test / scalacOptions -= "-Ywarn-dead-code", // For scala-mockito. See https://github.com/mockito/mockito-scala#dead-code-warning
    javacOptions ++= Seq(
      "-source", JDKVersion,
      "-target", JDKVersion,
      "-encoding", "UTF-8"
    ),
    resolvers := Resolvers.DefaultResolvers,
    libraryDependencies ++= Dependencies.Compile.All ++ Dependencies.Test.All,
      Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
  )
  .settings(dockerSettings: _*)
  .enablePlugins(JavaAppPackaging, DockerPlugin, AshScriptPlugin, GitVersioning)
