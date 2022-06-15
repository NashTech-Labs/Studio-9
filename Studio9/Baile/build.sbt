import Settings._
import Versions.JDKVersion
import sbt.Defaults.testSettings

lazy val makeDockerVersion = taskKey[Seq[File]]("Creates a docker-version.sbt file we can find at runtime.")

lazy val Baile = Project("baile", file("."))
  .settings(

    name := "baile",
    organization := "deepcortex",
    description := "User meta storage and Cortex job submitter",
    homepage := Some(url("https://github.com/deepcortex/baile")),
    coverageMinimum := 78.5,
    coverageFailOnMinimum := true,
    coverageExcludedPackages := Seq(
      "baile\\.bootstrap",
      "baile\\.HttpServerApp",
      "baile\\.dao\\.mongo\\.migrations",
      "baile\\.dao\\.mongo\\.migrations\\.history",
      "baile\\.routes\\.contract\\.images\\.augmentation",
      "baile\\.utils\\.MailService"  // TODO: find a way to test this (maybe refactor class itself)
    ).mkString(";"),

    Compile / mainClass := Some("baile.HttpServerApp"),

    scalaVersion := Versions.ScalaVersion,

    scalacOptions ++= Seq(
      s"-target:jvm-$JDKVersion",
      "-encoding", "UTF-8",
      "-Xfatal-warnings",
      "-Xlint:-unused,_",
      "-Ywarn-dead-code",
      "-Ywarn-unused:imports",             // Warn if an import selector is not referenced
      "-Ywarn-unused:locals",              // Warn if a local definition is unused
      "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused
      "-Ywarn-unused:privates",            // Warn if a private member is unused
      "-deprecation"
    ),

    Test / scalacOptions -= "-Ywarn-dead-code", // For scala-mockito. See https://github.com/mockito/mockito-scala#dead-code-warning

    javacOptions ++= Seq(
      "-source", JDKVersion,
      "-target", JDKVersion,
      "-encoding", "UTF-8"
    ),
    resolvers := Resolvers.DefaultResolvers,

    libraryDependencies ++= Dependencies.Compile.All ++ Dependencies.Test.All ++ Dependencies.IntegrationTest.All,

    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),

    inConfig(Configs.BaileIntegrationTest)(testSettings),

    migrateMongo := Tasks.migrateMongoTaskImpl.value
  )
  .settings(dockerSettings: _*)
  .settings(releaseSettings: _*)
  .configs(Configs.BaileIntegrationTest)
  .enablePlugins(JavaAppPackaging , DockerPlugin , AshScriptPlugin, GitVersioning)

/*
Here we add sed command to Dockerfile to form additional script for MongoMigrationApp
sbt native packager creates one, but it's not compatible with base file created by AshScriptPlugin
See https://github.com/sbt/sbt-native-packager/tree/v1.3.4/src/main/resources/com/typesafe/sbt/packager/archetypes/scripts
*/
import com.typesafe.sbt.packager.docker._

dockerCommands += Cmd(
  "RUN",
  "sed -e 's/app_mainclass=baile.HttpServerApp/app_mainclass=baile.dao.mongo.migrations.MongoMigrationApp/'",
  s"${ (defaultLinuxInstallLocation in Docker).value }/bin/${ executableScriptName.value }",
  s"> ${ (defaultLinuxInstallLocation in Docker).value }/bin/mongo-migration-app"
)
