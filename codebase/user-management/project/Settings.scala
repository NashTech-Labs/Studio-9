import Tasks._
import UMBuildCommons._
import com.typesafe.sbt.GitPlugin.autoImport.git
import com.typesafe.sbt.SbtNativePackager.autoImport.executableScriptName
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoPlugin.autoImport._
import sbtrelease.ReleasePlugin.autoImport.ReleaseKeys
import sbtrelease.ReleasePlugin.autoImport.ReleaseStep
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleasePlugin.autoImport.releaseProcess
import sbtrelease.ReleasePlugin.autoImport.releaseUseGlobalVersion
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.ReleaseStateTransformations.{setReleaseVersion ⇒ _}
import sbtrelease.Version
import sbtrelease.Versions
import sbtrelease.versionFormatError


object Settings {

  lazy val buildSettings = Seq(
    organization := "deepcortex",
    description := """API Services""",
    organizationHomepage := Some(url("https://www.deepcortex.us")),
    scalaVersion := UMBuildCommons.ScalaVersion,
    homepage := Some(url("https://github.com/deepcortex/user-management")),
    packageOptions += Package.ManifestAttributes(
      "Implementation-Version" -> (version in ThisBuild).value,
      "Implementation-Title" -> name.value
    ),
    updateOptions := updateOptions.value.withCachedResolution(cachedResoluton = true)
  )

  lazy val dockerSettings = Seq(
    dockerBaseImage := "openjdk:8-jre-alpine",
    dockerRepository := Some("deepcortex"),
    dockerEntrypoint := Seq("bin/%s" format executableScriptName.value),
    makeDockerVersion := makeDockerVersionTaskImpl.value
  )

  lazy val releaseSettings = Seq(
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      setReleaseVersion,
      runClean,
      runTest,
      tagRelease,
      releaseStepTask(stage in Docker),
      pushChanges
    ),
    git.baseVersion := "0.0.0",
    git.useGitDescribe := true,
    releaseVersionBump := sbtrelease.Version.Bump.Bugfix,
    releaseVersion     := { ver => Version(ver).map(_.withoutQualifier).map(_.bumpBugfix.string).getOrElse(versionFormatError) }
  )

  def setVersionOnly(selectVersion: Versions => String): ReleaseStep =  { st: State =>
    val vs = st.get(ReleaseKeys.versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
    val selected = selectVersion(vs)

    st.log.info("Setting version to '%s'." format selected)
    val useGlobal =Project.extract(st).get(releaseUseGlobalVersion)
    val versionStr = (if (useGlobal) globalVersionString else versionString) format selected

    reapply(Seq(if (useGlobal) version in ThisBuild := selected else version := selected), st)
  }

  lazy val setReleaseVersion: ReleaseStep = setVersionOnly(_._1)
}