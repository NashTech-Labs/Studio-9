import Common._
import Settings._
import Tasks._
import sbt.Keys._

name := "argo"

lazy val publishedProjects = Seq[ProjectReference](
  CommonDomainProject,
  DomainProject,
  CommonProject,
  TestKitProject,
  ArgoApiServiceProject,
  ArgoApiRestProject
)

lazy val root = BuildProject("argo-root", ".")
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

lazy val ArgoApiServiceProject = BuildProject("argo-api-service", "api-service")
  .settings(
    libraryDependencies ++= Dependencies.argoApiService
  )
  .dependsOn(CommonProject, DomainProject, TestKitProject % "test,it,e2e,bench")

lazy val ArgoApiRestProject: Project = BuildProject("argo-api-rest", "api-rest")
  .enablePlugins(JavaAppPackaging, DockerPlugin, AshScriptPlugin, BuildInfoPlugin, GitVersioning)
  .settings(
    libraryDependencies ++= Dependencies.argoApiRest,
    dockerExposedPorts := Seq(9000)
  )
  .settings(dockerSettings: _*)
  .settings(releaseSettings: _*)
  .settings(Settings.buildInfoSettings: _*)
  .dependsOn(ArgoApiServiceProject, TestKitProject % "test,it,e2e,bench")

gitHeadCommitSha in ThisBuild := gitHeadCommitShaDef
gitHeadCommitShaShort in ThisBuild := gitHeadCommitShaShortDef

addCommandAlias("all", "alias")
