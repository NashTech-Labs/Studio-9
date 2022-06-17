import Common._
import Settings._
import Tasks._
import sbt.Keys._

name := "taurus"

lazy val publishedProjects = Seq[ProjectReference](
  CommonDomainProject,
  DomainProject,
  CommonProject,
  TestKitProject,
  TaurusApiServiceProject,
  TaurusApiRestProject
)

lazy val root = BuildProject("taurus-root", ".")
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

lazy val TaurusApiServiceProject = BuildProject("taurus-api-service", "api-service")
  .settings(
    libraryDependencies ++= Dependencies.taurusApiService
  )
  .dependsOn(CommonProject, DomainProject, TestKitProject % "test,it,e2e,bench")

lazy val TaurusApiRestProject: Project = BuildProject("taurus", "api-rest")
  .enablePlugins(JavaAppPackaging, DockerPlugin, AshScriptPlugin, BuildInfoPlugin, GitVersioning)
  .settings(
    libraryDependencies ++= Dependencies.taurusApiRest,
    dockerExposedPorts := Seq(9000),
    fork := true
  )
  .settings(dockerSettings: _*)
  .settings(releaseSettings: _*)
  .settings(Settings.buildInfoSettings: _*)
  .dependsOn(TaurusApiServiceProject, TestKitProject % "test,it,e2e,bench")

gitHeadCommitSha in ThisBuild := gitHeadCommitShaDef
gitHeadCommitShaShort in ThisBuild := gitHeadCommitShaShortDef

addCommandAlias("all", "alias")
