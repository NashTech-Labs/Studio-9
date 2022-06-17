name := "um-client-sdk-play"

UMBuildCommons.settings

libraryDependencies ++= Seq(
  cache,
  "com.typesafe.play" %% "play-ws" % UMBuildCommons.playVersion,
  "org.scala-lang.modules" %% "scala-async" % "0.9.5",
  "org.mockito" % "mockito-core" % "1.9.5" % "test",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "com.typesafe.play" %% "play-test" % "2.4.6" % "test"
)

sbtbuildinfo.BuildInfoKeys.buildInfoPackage := "com.sentrana.um.client.play"

