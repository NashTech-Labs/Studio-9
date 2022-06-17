package com.sentrana.sbt.common.plugins

import java.text.SimpleDateFormat
import java.util.{ Date, TimeZone }

import sbt._
import sbt.Keys._

import sbtbuildinfo.{ BuildInfoKey, BuildInfoPlugin }
import sbtbuildinfo.BuildInfoKeys._

object BuildInfo {

  lazy val settings: Seq[Setting[_]] =
    BuildInfoPlugin.projectSettings ++
      baseSettings

  lazy val baseSettings = Seq(
    buildInfoKeys := baseBuildInfoKeys,
    buildInfoPackage := s"${organization.value}.${normalizedName.value}"
  )

  lazy val baseBuildInfoKeys = Seq[BuildInfoKey](
    name,
    version,
    scalaVersion,
    sbtVersion,
    buildDate
  )

  lazy val buildDate = BuildInfoKey.action("buildDate") {
    val utcTimeZone = TimeZone.getTimeZone("UTC")
    val isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'")
    isoDateFormat.setTimeZone(utcTimeZone)
    isoDateFormat.format(new Date())
  }
}
