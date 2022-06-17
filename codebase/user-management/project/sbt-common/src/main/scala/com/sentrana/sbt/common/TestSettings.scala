package com.sentrana.sbt.common

import sbt._
import sbt.Keys._

object TestSettings {

  lazy val settings: Seq[Setting[_]] = Seq(
    (testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports")
  )
}
