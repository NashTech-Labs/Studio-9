// *****************************************************************************
// Projects
// *****************************************************************************

lazy val `cortex-grpc` =
  project
    .in(file("."))
    .enablePlugins(GitVersioning)
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        library.log4jApi,
        library.log4jCore,
        library.typesafeConfig,
        library.playJson,
        library.json4s,
        library.json4sExt,
        library.awsS3      % Test,
        library.mockito    % Test,
        library.scalaCheck % Test,
        library.scalaTest  % Test
      )
    )

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val log4j          = "2.8.2"
      val mockito        = "2.8.47"
      val scalaCheck     = "1.13.5"
      val scalaTest      = "3.0.3"
      val typesafeConfig = "1.3.1"
      val aws            = "1.11.243"
      val playJson       = "2.6.9"
      val Json4s         = "3.5.2"
    }
    val log4jApi       = "org.apache.logging.log4j" %  "log4j-api"        % Version.log4j
    val log4jCore      = "org.apache.logging.log4j" %  "log4j-core"       % Version.log4j
    val mockito        = "org.mockito"              %  "mockito-core"     % Version.mockito
    val scalaCheck     = "org.scalacheck"           %% "scalacheck"       % Version.scalaCheck
    val scalaTest      = "org.scalatest"            %% "scalatest"        % Version.scalaTest
    val typesafeConfig = "com.typesafe"             %  "config"           % Version.typesafeConfig
    val awsS3          = "com.amazonaws"            %  "aws-java-sdk-s3"  % Version.aws
    val playJson       = "com.typesafe.play"        %% "play-json"        % Version.playJson
    val json4s         = "org.json4s"               %% "json4s-jackson"   % Version.Json4s
    val json4sExt      = "org.json4s"               %% "json4s-ext"       % Version.Json4s
  }

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
commonSettings ++
gitSettings ++
pbSettings ++
releaseSettings

lazy val commonSettings =
  Seq(
    scalaVersion := "2.11.11",
    crossScalaVersions := Seq("2.11.11", "2.12.4"),
    organization := "io.deepcortex",
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding", "UTF-8",
      "-Ywarn-unused-import"
    ),
    javacOptions ++= Seq(
      "-source", "1.8",
      "-target", "1.8"
    ),
    unmanagedSourceDirectories.in(Compile) :=
      Seq(scalaSource.in(Compile).value),
    unmanagedSourceDirectories.in(Test) :=
      Seq(scalaSource.in(Test).value)
  )

lazy val gitSettings =
  Seq(
      git.baseVersion := "0.0.0",
      git.useGitDescribe := true,
      git.gitTagToVersionNumber := {
        case VersionRegex(v,"") => Some(v)
        case VersionRegex(v,s) => Some(s"$v-$s")
        case _ => None
      }
  )
val VersionRegex = "([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r

import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, releaseProcess, releaseTagName}
import sbtrelease.Version
import sbtrelease.versionFormatError
import sbtrelease.ReleaseStateTransformations._
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
  releaseVersionBump := sbtrelease.Version.Bump.Bugfix,
  releaseVersion     := { ver => Version(ver).map(_.withoutQualifier).map(_.bumpBugfix.string).getOrElse(versionFormatError) },
  publishMavenStyle := true,
  publishTo := Some(Resolvers.DeepCortexRepo)
)

import com.trueaccord.scalapb.compiler.Version.scalapbVersion
lazy val pbSettings =
  Seq(
    PB.protoSources.in(Compile) := Seq(sourceDirectory.in(Compile).value / "proto"),
    PB.targets.in(Compile) := Seq(scalapb.gen(flatPackage = true) -> sourceManaged.in(Compile).value),
    libraryDependencies ++= Seq(
      "com.trueaccord.scalapb" %% "scalapb-runtime"      % scalapbVersion % "protobuf",
      "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion,
      "io.grpc"                % "grpc-netty"            % "1.4.0"
    )
  )

// *****************************************************************************
// Aliases
// *****************************************************************************

addCommandAlias("cstyle", ";compile;scalastyle")
addCommandAlias("ccstyle", ";clean;compile;scalastyle")
