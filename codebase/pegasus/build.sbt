import Common._
import Settings._
import Tasks._
import sbt.Keys._

name := "pegasus"

lazy val publishedProjects = Seq[ProjectReference](
  CommonDomainProject,
  DomainProject,
  CommonProject,
  TestKitProject,
  PegasusApiServiceProject,
  PegasusApiRestProject
)

lazy val root = BuildProject("pegasus-root", ".")
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

lazy val CommonDomainProject = BuildProject("common-domain")

lazy val DomainProject = BuildProject("domain")
  .dependsOn(CommonDomainProject)

lazy val CommonProject = BuildProject("common")
  .settings(
    libraryDependencies ++= Dependencies.common
  )
  .dependsOn(CommonDomainProject)

lazy val PegasusApiServiceProject = BuildProject("pegasus-api-service", "api-service")
  .settings(
    libraryDependencies ++= Dependencies.pegasusApiService
  )
  .dependsOn(CommonProject, DomainProject, TestKitProject % "test,it,e2e,bench")

lazy val PegasusApiRestProject: Project = BuildProject("pegasus-api-rest", "api-rest")
  .enablePlugins(JavaAppPackaging, DockerPlugin, AshScriptPlugin, BuildInfoPlugin, GitVersioning)
  .settings(
    libraryDependencies ++= Dependencies.pegasusApiRest,
    dockerExposedPorts := Seq(9000)
  )
  .settings(dockerSettings: _*)
  .settings(releaseSettings: _*)
  .settings(Settings.buildInfoSettings: _*)
  .dependsOn(PegasusApiServiceProject, TestKitProject % "test,it,e2e,bench")

gitHeadCommitSha in ThisBuild := gitHeadCommitShaDef
gitHeadCommitShaShort in ThisBuild := gitHeadCommitShaShortDef

addCommandAlias("all", "alias")
