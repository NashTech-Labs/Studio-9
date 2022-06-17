import sbt._

object Resolvers {
  lazy val DeepCortexRepo =
    "DeepCortex Internal Repository" at
      "s3://artifacts.deepcortex.ai.s3-us-east-1.amazonaws.com/maven/releases"

  lazy val RedshiftRepo =
    "redshift" at
      "https://s3.amazonaws.com/redshift-maven-repository/release"

  lazy val DefaultResolvers = Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.jcenterRepo,
    Resolvers.DeepCortexRepo,
    Resolvers.RedshiftRepo
  )
}
