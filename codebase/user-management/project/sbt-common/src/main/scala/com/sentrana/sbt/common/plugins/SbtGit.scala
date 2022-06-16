package com.sentrana.sbt.common.plugins

import sbt._

import com.typesafe.sbt.SbtGit._

object SbtGit {

  lazy val settings: Seq[Setting[_]] = showCurrentGitBranch
}
