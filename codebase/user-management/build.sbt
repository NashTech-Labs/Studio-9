name := "user-management"

UMBuildCommons.settings

lazy val root = (project in file(".")).
  aggregate(umService,
    umServiceShared,
    umClientSdkPlay/*,
    umClientSdkPlayAcceptance*/).
  settings(
      sbtbuildinfo.BuildInfoKeys.buildInfoPackage := "com.sentrana.um",
      aggregate in test := false /*to avoid running acceptance tests in incorrect environment*/
      ).
  configs(IntegrationTest).
  settings(Defaults.itSettings: _*).
  settings(UMBuildCommons.publishingSettings: _*)


parallelExecution in Test := true
parallelExecution in IntegrationTest := false

// Credentials for publishing to our internal repository
credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

lazy val umServiceShared = project.in(file("um-service-shared"))
lazy val umService = project.in(file("um-service"))

lazy val umClientSdkPlay = project.in(file("um-client-sdk-play")).
  dependsOn(umServiceShared).settings(UMBuildCommons.publishingSettings: _*)

lazy val umServiceTestLauncher = project.in(file("um-client-sdk-play-acceptance/um-service-test-launcher")).
  enablePlugins(PlayScala)

lazy val umClientSdkPlayAcceptance = project.in(file("um-client-sdk-play-acceptance")).
  enablePlugins(PlayScala).
  configs(IntegrationTest).
  settings(publish := {}).
  settings(Defaults.itSettings: _*).
  aggregate(umClientSdkPlay).
  settings(aggregate in test := false).
  dependsOn(umClientSdkPlay)

commands += AcceptanceTestUtils.acceptanceCommand
