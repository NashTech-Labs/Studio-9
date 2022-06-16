import sbt._

object Resolvers {
  lazy val DeepCortexRepo = "DeepCortex Internal Repository" at "s3://artifacts.deepcortex.ai.s3-us-east-1.amazonaws.com/maven/releases"
  lazy val ElasticSnapshots = "Elasticsearch Lucene Snapshots" at "https://download.elasticsearch.org/lucenesnapshots/89f6d17"

  lazy val DefaultResolvers = Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.jcenterRepo,
    Resolvers.DeepCortexRepo,
    Resolvers.ElasticSnapshots
  )
}