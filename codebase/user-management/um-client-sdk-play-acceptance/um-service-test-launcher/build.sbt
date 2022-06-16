name := "um-service-test-launcher"

UMBuildCommons.settings

val umServiceVersion = System.getProperty("umServiceVersion")

if(umServiceVersion != null) {
    //TODO couldn't find a way to use logging outside of task definition
    println(s"Using um-service version $umServiceVersion")
    libraryDependencies += UMBuildCommons.umOrganization %% "um-service" % umServiceVersion
} else {
    println("Using default um-service version")
    libraryDependencies += UMBuildCommons.umOrganization %% "um-service" % version.value
}

sbtbuildinfo.BuildInfoKeys.buildInfoPackage := "com.sentrana.um.acceptance.server.test.launcher"

commands += AcceptanceTestUtils.launchWithEmbeddedMongoCommand

