import Common._
import Tasks._
import com.scalapenos.sbt.prompt.SbtPrompt.autoImport._
import com.scalapenos.sbt.prompt._
import com.typesafe.sbt.GitPlugin.autoImport.git
import com.typesafe.sbt.SbtNativePackager.autoImport.executableScriptName
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.stage
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

import scala.language.postfixOps
import scalariform.formatter.preferences.AlignArguments
import scalariform.formatter.preferences.AlignParameters
import scalariform.formatter.preferences.AlignSingleLineCaseStatements
import scalariform.formatter.preferences.DoubleIndentClassDeclaration

object Settings {

  lazy val buildSettings = Seq(
    organization := "deepcortex",
    description := """API Services""",
    organizationHomepage := Some(url("https://www.deepcortex.us")),
    scalaVersion := Versions.ScalaVersion,
    homepage := Some(url("https://github.com/deepcortex/aries")),
    packageOptions += Package.ManifestAttributes(
      "Implementation-Version" -> (version in ThisBuild).value,
      "Implementation-Title" -> name.value
    ),
    promptTheme := promptThemeValue(version.value),
    updateOptions := updateOptions.value.withCachedResolution(cachedResoluton = true)
  )

  lazy val notPublish = Seq(
    publishArtifact := false,
    publish := {},
    publishLocal := {},
    publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo")))
  )

  lazy val defaultSettings = testSettings ++ Seq(
    scalacOptions ++= commonScalacOptions,
    javacOptions in Compile ++= commonJavacOptions,
    ivyLoggingLevel in ThisBuild := UpdateLogging.Quiet,
    maxErrors := 20,
    pollInterval := 1000,
    offline := true,
    initialCommands := initialCommandsValue.mkString("\n"),
    initialCommands in console += "//import deepcortex._",
    initialCommands in (Compile, consoleQuick) := (initialCommands in Compile).value,
    resolvers := Resolvers.DefaultResolvers
  ) ++ scalariformSettings

  lazy val dockerSettings = Seq(
      dockerBaseImage := "openjdk:8-jre-alpine",
      dockerRepository := Some(organization.value),
      dockerEntrypoint := Seq("bin/%s" format executableScriptName.value, "-Dconfig.resource=docker.conf"),
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

  lazy val buildInfoSettings = Seq(
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
      buildInfoPackage := "aries.common",
      buildInfoOptions ++= Seq(BuildInfoOption.BuildTime, BuildInfoOption.ToJson)
  )

  lazy val testSettings = Seq(
      parallelExecution in Test := false,
      fork in Test := true,
      testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework")
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


  private val scalariformSettings = Seq(ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(AlignParameters, true)
      .setPreference(AlignArguments, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true))

  private val initialCommandsValue = Seq[String](
    """
      |import System.{currentTimeMillis => now}
      |def time[T](f: => T): T = {
      |  val start = now
      |  try { f } finally { println("Elapsed: " + (now - start) + " ms") }
      |}
      | """.stripMargin
  )

  private def promptThemeValue(version: String) = PromptTheme(List(
    text("SBT", NoStyle),
    text(" | ", NoStyle),
    gitBranch(clean = NoStyle, dirty = fg(red)),
    text(s" / $version", NoStyle),
    text(" | ", NoStyle),
    currentProject(NoStyle),
    text(" > ", NoStyle)
  ))
}
