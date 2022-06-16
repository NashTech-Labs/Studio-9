lazy val rootBuild = project.in(file(".")).dependsOn(sbtCommon)
lazy val sbtCommon = file("sbt-common")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.5")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.7.0")