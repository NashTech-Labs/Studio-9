package com.sentrana.sbt.common

import sbt._
import sbt.plugins.JvmPlugin

import play.PlayScala

import com.sentrana.sbt.common.plugins.Play

object SentranaPlay extends AutoPlugin {

  override def requires = JvmPlugin && PlayScala

  override def trigger = allRequirements

  override def projectSettings: Seq[Setting[_]] = Play.settings
}
