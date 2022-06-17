package com.sentrana.sbt.common.plugins

import java.io.File

import sbt._
import sbt.Keys._

import org.scalastyle.sbt.ScalastylePlugin
import org.scalastyle.sbt.ScalastylePlugin._

object Scalastyle {

  lazy val settings: Seq[Setting[_]] =
    ScalastylePlugin.projectSettings ++
      baseSettings ++
      testSettings ++
      Project.inConfig(Compile)(baseSettings) ++
      Project.inConfig(Test)(testSettings)

  lazy val baseSettings = Seq(
    scalastyleConfig := mainConfigFile.value,
    scalastyleTarget := mainOutputFile.value
  )

  lazy val testSettings = Seq(
    (scalastyleConfig in Test) := testConfigFile.value,
    (scalastyleTarget in Test) := testOutputFile.value,

    // Temporary fix for https://github.com/scalastyle/scalastyle-sbt-plugin/issues/44
    (scalastyleConfig in scalastyle) := testConfigFile.value,
    (scalastyleTarget in scalastyle) := testOutputFile.value
  )

  lazy val mainConfigFile = configFile("scalastyle-config.xml")
  lazy val testConfigFile = configFile("scalastyle-test-config.xml")

  def configFile(fileName: String) = baseDirectory { baseDir =>
    lazy val inBaseDir = baseDir / fileName
    lazy val inCommonDir = sbtCommonDir(baseDir) / fileName
    if (inBaseDir.exists) inBaseDir else inCommonDir
  }

  def sbtCommonDir(baseDir: File) = baseDir / "project" / "sbt-common"

  lazy val mainOutputFile = target { _ / "checkstyle-scalastyle.xml" }
  lazy val testOutputFile = target { _ / "checkstyle-scalastyle-test.xml" }
}
