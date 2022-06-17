name := "um-client-sdk-play-acceptance"

UMBuildCommons.settings

libraryDependencies ++= Seq(
  ws,
  "org.mongodb.scala" %% "mongo-scala-driver" % "1.1.0" % "it",
  "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "1.50.2" % "it",
  "org.scalatestplus" %% "play" % "1.4.0" % "it,test",
  "com.github.simplyscala" %% "scalatest-embedmongo" % "0.2.2" % "it",
//  "dumbster" % "dumbster" % "1.6" % "it"
"com.github.kirviq" % "dumbster" % "1.7.1" % "it"

)

sourceDirectory in IntegrationTest := baseDirectory.value / "it"

Keys.fork in IntegrationTest := true
javaOptions in IntegrationTest += "-Dconfig.resource=acceptance.conf"

sbtbuildinfo.BuildInfoKeys.buildInfoPackage := "com.sentrana.um.client.play.acceptance"

routesGenerator := InjectedRoutesGenerator

