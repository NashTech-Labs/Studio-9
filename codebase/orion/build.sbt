import Common._
import Settings._
import Tasks._
import sbt.Keys.{parallelExecution, _}

name := "orion"

lazy val publishedProjects = Seq[ProjectReference](
  CommonDomainProject,
  DomainProject,
  CommonProject,
  TestKitProject,
  OrionApiServiceProject,
  OrionApiRestProject
)

lazy val root = BuildProject("orion-root", ".")
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
  ).dependsOn(CommonDomainProject)

lazy val OrionApiServiceProject = BuildProject("orion-api-service", "api-service")
  .settings(
    libraryDependencies ++= Dependencies.orionApiService,
    parallelExecution in Test := false
  )
  .dependsOn(CommonProject, DomainProject, TestKitProject % "test,it,e2e,bench")

lazy val OrionApiRestProject: Project = BuildProject("orion-api-rest", "api-rest")
  .enablePlugins(JavaAppPackaging, DockerPlugin, AshScriptPlugin, BuildInfoPlugin, GitVersioning)
  .settings(
    libraryDependencies ++= Dependencies.orionApiRest,
    dockerExposedPorts := Seq(9000, 2552),
    fork := true
  )
  .settings(dockerSettings: _*)
  .settings(releaseSettings: _*)
  .settings(Settings.buildInfoSettings: _*)
  .dependsOn(OrionApiServiceProject, TestKitProject % "test,it,e2e,bench")

gitHeadCommitSha in ThisBuild := gitHeadCommitShaDef
gitHeadCommitShaShort in ThisBuild := gitHeadCommitShaShortDef

addCommandAlias("all", "alias")
