import sbt._

object Resolvers {
  lazy val DeepCortexRepo = "DeepCortex Internal Repository" at "s3://artifacts.deepcortex.ai.s3-us-east-1.amazonaws.com/maven/releases"
  lazy val Redshift = "Redshift" at "http://redshift-maven-repository.s3-website-us-east-1.amazonaws.com/release"

  lazy val DefaultResolvers = Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.jcenterRepo,
    Resolvers.DeepCortexRepo,
    Resolvers.Redshift
  )
}