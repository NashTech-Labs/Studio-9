import sbt._

object Resolvers {

  lazy val DeepCortexRepo = "DeepCortex Internal Repository" at "s3://artifacts.deepcortex.ai.s3-us-east-1.amazonaws.com/maven/releases"

  lazy val DefaultResolvers: Seq[MavenRepository] = Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.jcenterRepo,
    Resolver.bintrayRepo("dnvriend", "maven"),
    Resolvers.DeepCortexRepo
  )

}
