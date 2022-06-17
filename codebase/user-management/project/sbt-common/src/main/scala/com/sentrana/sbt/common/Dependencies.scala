package com.sentrana.sbt.common

import sbt._
import sbt.Keys._

object Dependencies {

  // A

  val apacheHttpVersion = "4.3.6"
  val apacheHttpClient = "org.apache.httpcomponents" % "httpclient" % apacheHttpVersion
  val apacheHttpMime = "org.apache.httpcomponents" % "httpmime" % apacheHttpVersion

  // C

  val casbah = "org.mongodb" %% "casbah" % "2.8.0"

  val commonsIo = "commons-io" % "commons-io" % "2.4"

  // E

  val embeddedMongo = "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "1.47.3" % "test"

  // F

  val forceRestAPI = "com.force.api" % "force-rest-api" % "0.0.20"

  // J

  val jongo = "org.jongo" % "jongo" % "1.2"

  val json4sVersion = "3.2.11"
  val json4sExt = "org.json4s" %% "json4s-ext" % json4sVersion
  val json4sMongo = "org.json4s" %% "json4s-mongo" % json4sVersion
  val json4sNative = "org.json4s" %% "json4s-native" % json4sVersion

  // M

  val mysql = "mysql" % "mysql-connector-java" % "5.1.28"

  // N

  val nscalaTime = "com.github.nscala-time" %% "nscala-time" % "1.6.0"

  // P

  val playMailerPlugin = "com.typesafe.play" %% "play-mailer" % "4.0.0"

  val postgresql = "postgresql" % "postgresql" % "9.1-901-1.jdbc4"

  val prefuse = "org.prefuse" % "prefuse" % "beta-20071021"

  // S

  val scalaCSV = "com.github.tototoshi" %% "scala-csv" % "1.1.2"

  val scalaReflect = scalaVersion("org.scala-lang" % "scala-reflect" % _)

  val scalaTestPlusPlay = "org.scalatestplus" %% "play" % "1.4.0" % "test"

  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.0.3"

  val scalikeJDBCVersion = "2.2.4"
  val scalikeJDBCCore = "org.scalikejdbc" %% "scalikejdbc" % scalikeJDBCVersion
  val scalikeJDBCTest = "org.scalikejdbc" %% "scalikejdbc-test" % scalikeJDBCVersion % "test"

  val squeryl = "org.squeryl" %% "squeryl" % "0.9.5-7"

  // T

  val twitterUtilCore = "com.twitter" %% "util-core" % "6.22.0"
}
