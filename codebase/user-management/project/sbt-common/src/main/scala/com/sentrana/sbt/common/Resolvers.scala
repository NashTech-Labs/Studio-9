package com.sentrana.sbt.common

import sbt._

object Resolvers {

  lazy val allResolvers = Seq(
    forceRestAPIReleases,
    sonatypeReleases,
    typesafeReleases
  )

  val forceRestAPIReleases = "Force REST API releases" at "https://jesperfj.github.io/force-rest-api/repository/"

  val sonatypeReleases = "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases/"

  val typesafeReleases = "Typesafe releases" at "https://repo.typesafe.com/typesafe/releases/"
}
