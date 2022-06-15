import Tasks.makeDockerVersionTaskImpl
import com.typesafe.sbt.GitPlugin.autoImport.git
import com.typesafe.sbt.SbtNativePackager.autoImport.executableScriptName
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.stage
import sbt.Keys.organization
import sbt.{ File, taskKey }
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.{ Version, versionFormatError }

object Settings {

  lazy val makeDockerVersion = taskKey[Seq[File]]("Creates a docker-version.sbt file we can find at runtime.")

  lazy val dockerSettings = Seq(
    dockerBaseImage := "openjdk:8-jre-alpine",
    dockerRepository := Some(organization.value),
    dockerEntrypoint := Seq("bin/%s" format executableScriptName.value),
    dockerBuildOptions := Seq("--force-rm", "-t", dockerAlias.value.versioned),
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
    releaseVersion := { ver => Version(ver).map(_.withoutQualifier).map(_.bumpBugfix.string).getOrElse(versionFormatError) }
  )

}
