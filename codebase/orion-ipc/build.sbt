import Common._
import Settings._
import Tasks._
import sbt.Keys._

name := "orion-ipc"

lazy val publishedProjects = Seq[ProjectReference](
  TestKitProject,
  CommonProject,
  OrionIpcRabbitmqProject
)

lazy val root = BuildProject("orion-ipc-root", ".")
  .settings(notPublish: _*)
  .settings(Testing.settings: _*)
  .aggregate(publishedProjects: _*)

// ---------------------------------------------------------------------------------------------------------------------
// Modules
// ---------------------------------------------------------------------------------------------------------------------

lazy val TestKitProject = BuildProject("orion-ipc-testkit", "testkit")
  .settings(
    libraryDependencies ++= Dependencies.testKit
  )
  .settings(notPublish: _*)
  .dependsOn(CommonProject)

lazy val CommonProject = BuildProject("orion-ipc-common", "common")
  .settings(
    libraryDependencies ++= Dependencies.common
  )
  .settings(Settings.releaseSettings: _*)

lazy val OrionIpcRabbitmqProject = BuildProject("orion-ipc-rabbitmq", "rabbitmq")
  .enablePlugins(JavaAppPackaging, BuildInfoPlugin, GitVersioning)
  .settings(
    libraryDependencies ++= Dependencies.orionIpcRabbitmq
  )
  .settings(releaseSettings: _*)
  .settings(Settings.buildInfoSettings: _*)
  .dependsOn(CommonProject, TestKitProject % "test,it,e2e,bench")

gitHeadCommitSha in ThisBuild := gitHeadCommitShaDef
gitHeadCommitShaShort in ThisBuild := gitHeadCommitShaShortDef

addCommandAlias("all", "alias")
