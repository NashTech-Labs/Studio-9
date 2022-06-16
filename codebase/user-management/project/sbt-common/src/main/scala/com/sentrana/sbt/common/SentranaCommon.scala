package com.sentrana.sbt.common

import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin

import com.sentrana.sbt.common.plugins._

object SentranaCommon extends AutoPlugin {

  override def requires = JvmPlugin

  override def trigger = allRequirements

  override def projectSettings: Seq[Setting[_]] =
    commonSettings ++
      BuildSettings.settings ++
      BuildInfo.settings ++
      SbtGit.settings ++
      Scalariform.settings ++
      Scalastyle.settings ++
      aliases

  lazy val commonSettings = Seq(
    organization := "com.sentrana",
    scalaVersion := "2.11.7",
    resolvers ++= Resolvers.allResolvers
  )

  lazy val aliases =
    addCommandAlias("format", "scalariformFormat") ++
      addCommandAlias("style", "scalastyle")

}
