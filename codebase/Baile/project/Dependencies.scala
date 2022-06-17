import Versions._
import sbt._

object Dependencies {

  object Compile {
    val TypeSafeConfig         = "com.typesafe"                  %  "config"                            % TypeSafeConfigVersion
    val AkkaActor              = "com.typesafe.akka"             %% "akka-actor"                        % AkkaVersion
    val AkkaStream             = "com.typesafe.akka"             %% "akka-stream"                       % AkkaVersion
    val AkkaCluster            = "com.typesafe.akka"             %% "akka-cluster"                      % AkkaVersion
    val AkkaSingletonCluster   = "com.typesafe.akka"             %% "akka-cluster-tools"                % AkkaVersion
    val AkkaZKClusterSeed      = "com.sclasen"                   %% "akka-zk-cluster-seed"              % AkkaZKClusterSeedVersion
    val AkkaHttp               = "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion
    val AkkaHttpCors           = "ch.megard"                     %% "akka-http-cors"                    % AkkaHttpCorsVersion
    val AkkaSlf4j              = "com.typesafe.akka"             %% "akka-slf4j"                        % AkkaVersion
    val Logback                = "ch.qos.logback"                %  "logback-classic"                   % LogbackVersion
    val AwsScala               = "com.github.seratch"            %% "awscala"                           % AwsScalaVersion
    val PlayJson               = "com.typesafe.play"             %% "play-json"                         % PlayJsonVersion
    val PlayJsonTraits         = "io.leonard"                    %% "play-json-traits"                  % PlayJsonTraitsVersion
    val AkkaHttpPlayJson       = "de.heikoseeberger"             %% "akka-http-play-json"               % AkkaHttpPlayJsonVersion
    val CortexGRPC             = "io.deepcortex"                 %% "cortex-grpc"                       % CortexGRPCVersion
    val MongoDBScalaDriver     = "org.mongodb.scala"             %% "mongo-scala-driver"                % MongoDBScalaDriverVersion
    val Cats                   = "org.typelevel"                 %% "cats-core"                         % CatsVersion
    val AkkaHttpCache          = "com.typesafe.akka"             %% "akka-http-caching"                 % AkkaHttpCacheVersion
    val JavaXMailApi           = "com.sun.mail"                  %  "javax.mail"                        % JavaXMailApiVersion
    val ScalikeJDBC            = "org.scalikejdbc"               %% "scalikejdbc"                       % ScalikeJDBCVersion
    val RedshiftJDBC           = "com.amazon.redshift"           %  "redshift-jdbc42"                   % RedshiftJDBCVersion
    val AlpakkaCSV             = "com.lightbend.akka"            %% "akka-stream-alpakka-csv"           % AlpakkaVersion
    val AlpakkaMongoDB         = "com.lightbend.akka"            %% "akka-stream-alpakka-mongodb"       % AlpakkaVersion
    val AlpakkaS3              = "com.lightbend.akka"            %% "akka-stream-alpakka-s3"            % AlpakkaVersion
    val ScalaTags              = "com.lihaoyi"                   %% "scalatags"                         % ScalaTagsVersion
    val GraphCore              = "org.scala-graph"               %% "graph-core"                        % GraphCoreVersion
    val ApacheCompressCommons  = "org.apache.commons"            % "commons-compress"                   % ApacheCompressCommonsVersion
    val PlayJsonCats           = "com.iravid"                    %% "play-json-cats"                    % PlayJsonCatsVersion

    val All = Seq(
      TypeSafeConfig,
      AkkaActor,
      AkkaStream,
      AkkaCluster,
      AkkaSingletonCluster,
      AkkaZKClusterSeed,
      AkkaHttp,
      AkkaHttpCors,
      AkkaSlf4j,
      Logback,
      AwsScala,
      PlayJson,
      PlayJsonTraits,
      AkkaHttpPlayJson,
      CortexGRPC,
      MongoDBScalaDriver,
      Cats,
      AkkaHttpCache,
      JavaXMailApi,
      ScalikeJDBC,
      RedshiftJDBC,
      AlpakkaCSV,
      AlpakkaMongoDB,
      AlpakkaS3,
      ScalaTags,
      GraphCore,
      ApacheCompressCommons,
      PlayJsonCats
    )
  }

  object Test {
    val AkkaTestKit               = "com.typesafe.akka"             %% "akka-testkit"             % AkkaVersion
    val AkkaHttpTestKit           = "com.typesafe.akka"             %% "akka-http-testkit"        % AkkaHttpVersion
    val ScalaTest                 = "org.scalatest"                 %% "scalatest"                % ScalaTestVersion
    val Mockito                   = "org.mockito"                   %  "mockito-core"             % MockitoVersion
    val MockitoScala              = "org.mockito"                   %% "mockito-scala"            % MockitoScalaVersion
    val MockJavaMail              = "org.jvnet.mock-javamail"       % "mock-javamail"             % MockJavaMailVersion

    val All = Seq(
      AkkaTestKit,
      AkkaHttpTestKit,
      ScalaTest,
      Mockito,
      MockJavaMail,
      MockitoScala
    ).map(_ % Configurations.Test)
  }

  object IntegrationTest {
    val DockerIt                  = "com.whisk"                     %% "docker-testkit-scalatest"     % DockerItVersion
    val DockerItSpotifyImpl       = "com.whisk"                     %% "docker-testkit-impl-spotify"  % DockerItVersion

    val All = Seq(
      DockerIt,
      DockerItSpotifyImpl
    ).map(_ % Configs.BaileIntegrationTest.name)
  }

}
