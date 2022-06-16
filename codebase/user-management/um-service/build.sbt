//import com.sentrana.sbt.common.Dependencies._
import Tasks._
import Settings._

name := "um-service"

UMBuildCommons.settings

val scalikejdbcVersion = "2.4.2"

libraryDependencies ++= Seq(
  cache,
  "io.netty" % "netty-transport" % "4.0.0.Alpha7",
  "io.netty" % "netty-handler" % "4.1.0.CR7",
  "org.mongodb.scala" %% "mongo-scala-driver" % "1.1.0",
  "org.mockito" % "mockito-core" % "1.9.5" % "test",
  "org.scalatestplus" %% "play" % "1.4.0" % "it,test",
  "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "1.50.2" % "it",
  "com.github.simplyscala" %% "scalatest-embedmongo" % "0.2.2" % "it",
  "org.scala-lang.modules" %% "scala-async" % "0.9.5",
  "com.iheart" %% "play-swagger" % "0.3.2",
  "org.opensaml" % "opensaml" % "2.6.4",
  "com.h2database" % "h2" % "1.4.192",
  "org.scalikejdbc" %% "scalikejdbc-core" % scalikejdbcVersion,
  "org.scalikejdbc" %% "scalikejdbc-config" % scalikejdbcVersion,
  "org.scalikejdbc" %% "scalikejdbc" % scalikejdbcVersion,
  "com.typesafe.play" %% "play-mailer" % "4.0.0"
)

//Adds separate configuration file for test configuration
javaOptions in Test += "-Dconfig.resource=unit_test.conf"

sourceDirectory in IntegrationTest := baseDirectory.value / "it"

parallelExecution in Test := true
parallelExecution in IntegrationTest := false

lazy val umService = (project in file(".")).
  aggregate(umServiceShared).
  dependsOn(umServiceShared).
  enablePlugins(PlayScala, DockerPlugin, AshScriptPlugin, GitVersioning, UniversalPlugin).
  settings(sbtbuildinfo.BuildInfoKeys.buildInfoPackage := "com.sentrana.umserver",
  dockerExposedPorts := Seq(9000)).
  configs(IntegrationTest).
  settings(Defaults.itSettings: _*).
  settings(testOptions in IntegrationTest += Tests.Setup(() =>
    if(System.getProperty("config.resource") == null && System.getProperty("config.file") == null)
      System.setProperty("config.resource", "integration.conf")
  )).settings(UMBuildCommons.publishingSettings: _*)
  .settings(dockerSettings: _*)
  .settings(releaseSettings: _*)

lazy val umServiceShared = project.in(file("um-service-shared")).settings(UMBuildCommons.publishingSettings: _*)

routesGenerator := InjectedRoutesGenerator
