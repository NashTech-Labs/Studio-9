name := "um-service-shared"

UMBuildCommons.settings

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % UMBuildCommons.playVersion % Provided,
  "com.typesafe.play" %% "play" % UMBuildCommons.playVersion % Provided,
  "org.scala-lang.modules" %% "scala-async" % "0.9.5" % Provided,
  "org.scalatest" %% "scalatest" % "2.2.6" % "test"
)

sbtbuildinfo.BuildInfoKeys.buildInfoPackage := "com.sentrana.umserver.shared"
