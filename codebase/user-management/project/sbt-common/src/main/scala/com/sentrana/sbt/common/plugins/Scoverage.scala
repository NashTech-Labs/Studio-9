package com.sentrana.sbt.common.plugins

import sbt._

import scoverage.ScoverageKeys._

object Scoverage {

  lazy val settings: Seq[Setting[_]] = Seq(
    coverageExcludedPackages := "<empty>;Reverse.*"
  )
}
