import Common._
import Settings._
import Tasks._
import sbt.Keys._

name := "aries"

lazy val publishedProjects = Seq[ProjectReference](
  CommonDomainProject,
  DomainProject,
  CommonProject,
  TestKitProject,
  AriesApiServiceProject,
  AriesApiRestProject
)

lazy val root = BuildProject("aries-root", ".")
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

lazy val AriesApiServiceProject = BuildProject("aries-api-service", "api-service")
  .settings(
    libraryDependencies ++= Dependencies.ariesApiService
  )
  .dependsOn(CommonProject, DomainProject, TestKitProject % "test,it,e2e,bench")

lazy val AriesApiRestProject: Project = BuildProject("aries-api-rest", "api-rest")
  .enablePlugins(JavaAppPackaging, DockerPlugin, AshScriptPlugin, BuildInfoPlugin, GitVersioning)
  .settings(
    libraryDependencies ++= Dependencies.ariesApiRest,
    dockerExposedPorts := Seq(9000)
  )
  .settings(dockerSettings: _*)
  .settings(releaseSettings: _*)
  .settings(Settings.buildInfoSettings: _*)
  .dependsOn(AriesApiServiceProject, TestKitProject % "test,it,e2e,bench")

gitHeadCommitSha in ThisBuild := gitHeadCommitShaDef
gitHeadCommitShaShort in ThisBuild := gitHeadCommitShaShortDef

addCommandAlias("all", "alias")

