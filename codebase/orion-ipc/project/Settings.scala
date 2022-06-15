import Common._
import com.scalapenos.sbt.prompt.SbtPrompt.autoImport._
import com.scalapenos.sbt.prompt._
import com.typesafe.sbt.GitPlugin.autoImport.git
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoPlugin.autoImport._
import sbtrelease.ReleasePlugin.autoImport.{ReleaseKeys, ReleaseStep, releaseProcess, releaseTagName, releaseUseGlobalVersion, _}
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
    organization := "ai.deepcortex.orion",
    description := """Inter-process communication Services""",
    organizationHomepage := Some(url("https://www.deepcortex.ai")),
    scalaVersion := Versions.ScalaVersion,
    homepage := Some(url("https://github.com/deepcortex/orion-ipc")),
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
    initialCommands in console += "//import orion.ipc._",
    initialCommands in (Compile, consoleQuick) := (initialCommands in Compile).value,
    resolvers := Resolvers.defaultResolvers
  ) ++ scalariformSettings

  val VersionRegex = "([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r

  lazy val releaseSettings = Seq(
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      setReleaseVersion,
      runClean,
      runTest,
      tagRelease,
      pushChanges
    ),
    releaseTagName := releaseTagName.value drop 1,
    git.baseVersion := "0.0.0",
    git.useGitDescribe := true,
    git.gitTagToVersionNumber := {
      case VersionRegex(v,"SNAPSHOT") => Some(s"$v-SNAPSHOT")
      case VersionRegex(v,"") => Some(v)
      case VersionRegex(v,s) => Some(s"$v-$s-SNAPSHOT")
      case v => None
    },
    releaseVersionBump := sbtrelease.Version.Bump.Bugfix,
    releaseVersion     := { ver => Version(ver).map(_.withoutQualifier).map(_.bumpBugfix.string).getOrElse(versionFormatError) },
    publishMavenStyle := true,
    publishTo := {
      if (isSnapshot.value) Some(Resolvers.DeepCortexRepoSnapshots) else Some(Resolvers.DeepCortexRepo)
    }
  )

  lazy val buildInfoSettings = Seq(
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
      buildInfoPackage := "orion.ipc.common",
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
    text(s" / ${version.replace("SNAPSHOT-SNAPSHOT", "SNAPSHOT")}", NoStyle),
    text(" | ", NoStyle),
    currentProject(NoStyle),
    text(" > ", NoStyle)
  ))
}
