import sbt._

object Resolvers {
  lazy val DeepCortexDevReleaseRepo = "DeepCortex Releases Internal Repository" at "s3://artifacts.deepcortex.ai.s3-us-east-1.amazonaws.com/maven/releases"
  lazy val DeepCortexDevSnapshotRepo = "DeepCortex Snapshots Internal Repository" at "s3://artifacts.deepcortex.ai.s3-us-east-1.amazonaws.com/maven/snapshots"

  lazy val defaultResolvers = Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.jcenterRepo,
    Resolvers.DeepCortexDevReleaseRepo,
    Resolvers.DeepCortexDevSnapshotRepo
  )
}