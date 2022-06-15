import Common._
import Settings._
import Tasks._
import sbt.Keys._

name := "cortex-job-master"

lazy val publishedProjects = Seq[ProjectReference](
  TestKitProject,
  CommonProject,
  JobMasterProject,
  SchedulerProject
)

lazy val root = BuildProject("cortex-job-master-root", ".")
  .settings(notPublish: _*)
  .settings(Testing.settings: _*)
  .aggregate(publishedProjects: _*)

// ---------------------------------------------------------------------------------------------------------------------
// Modules
// ---------------------------------------------------------------------------------------------------------------------

lazy val TestKitProject = BuildProject("testkit")
  .settings(
    libraryDependencies ++= Dependencies.testkit
  )
  .settings(notPublish: _*)
  .dependsOn(CommonProject)

lazy val CommonProject = BuildProject("common")
  .settings(
    libraryDependencies ++= Dependencies.common
  )

lazy val SchedulerProject = BuildProject("scheduler")
  .settings(
    libraryDependencies ++= Dependencies.scheduler
  )
  .dependsOn(TestKitProject % "test,it,e2e,bench")
  .dependsOn(CommonProject)

lazy val JobMasterProject: Project = BuildProject("cortex-job-master", "job-master")
  .enablePlugins(JavaAppPackaging, DockerPlugin, BuildInfoPlugin, GitVersioning)
  .settings(
    libraryDependencies ++= Dependencies.cortexJobMaster,
    dockerExposedPorts := Seq(9041)
  )
  .settings(dockerSettings: _*)
  .settings(releaseSettings: _*)
  .settings(Settings.buildInfoSettings: _*)
  .dependsOn(CommonProject, SchedulerProject % "e2e->e2e;test->test;compile->compile",
    TestKitProject % "test,it,e2e,bench")

gitHeadCommitSha in ThisBuild := gitHeadCommitShaDef
gitHeadCommitShaShort in ThisBuild := gitHeadCommitShaShortDef

addCommandAlias("all", "alias")
