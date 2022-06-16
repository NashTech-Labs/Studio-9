import Settings._
import Versions._
import sbt._

/**
  * Common settings/definitions for the build
  */
object Common {

  //noinspection ScalaStyle
  def BuildProject(name: String, dir: String): Project = Project(name, file(dir))
    .configs(Configs.all: _*)
    .settings(buildSettings: _*)
    .settings(defaultSettings: _*)
    .settings(Testing.settings: _*)

  //noinspection ScalaStyle
  def BuildProject(name: String): Project = BuildProject(name, name)

  val commonScalacOptions = Seq(
    s"-target:jvm-$JDKVersion",
    "-encoding", "UTF-8",
    "-feature",
    "-deprecation",
    "-unchecked",
    "-language:_",
    "-Xfatal-warnings",
    "-Xfuture",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen"
  )

  val commonJavacOptions = Seq(
    "-source", JDKVersion,
    "-target", JDKVersion,
    "-encoding", "UTF-8",
    "-Xlint:deprecation",
    "-Xlint:unchecked"
  )

  val fgColor = 235
}