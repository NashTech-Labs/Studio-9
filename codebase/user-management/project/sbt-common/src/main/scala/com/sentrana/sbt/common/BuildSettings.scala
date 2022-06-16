package com.sentrana.sbt.common

import sbt._
import sbt.Keys._

object BuildSettings {

  lazy val settings: Seq[Setting[_]] = Seq(
    scalacOptions ++= compilerFlags
  )

  lazy val compilerFlags = Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xfuture",
    "-Xlint",
    "-Xlint:-missing-interpolator", // https://github.com/playframework/playframework/issues/5134
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  )
}
