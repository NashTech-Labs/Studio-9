import Versions._
import sbt._

object Dependencies {

  object Compile {
    val TypeSafeConfig            = "com.typesafe"                  %  "config"                            % TypeSafeConfigVersion
    val AkkaActor                 = "com.typesafe.akka"             %% "akka-actor"                        % AkkaVersion
    val AkkaHttp                  = "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion
    val AkkaStream                = "com.typesafe.akka"             %% "akka-stream"                       % AkkaVersion
    val AkkaCluster               = "com.typesafe.akka"             %% "akka-cluster"                      % AkkaVersion
    val AkkaZKClusterSeed         = "com.sclasen"                   %% "akka-zk-cluster-seed"              % AkkaZKClusterSeedVersion
    val AkkaPersistence           = "com.typesafe.akka"             %% "akka-persistence"                  % AkkaVersion
    val AkkaClusterSharding       = "com.typesafe.akka"             %% "akka-cluster-sharding"             % AkkaVersion
    val PlayJson                  = "com.typesafe.play"             %% "play-json"                         % PlayJsonVersion
    val AkkaHttpPlayJson          = "de.heikoseeberger"             %% "akka-http-play-json"               % AkkaHttpPlayJsonVersion
    val Cats                      = "org.typelevel"                 %% "cats-core"                         % CatsVersion
    val MarathonClient            = "com.mesosphere"                %  "marathon-client"                   % MarathonClientVersion
    val CortexGRPC                = "io.deepcortex"                 %% "cortex-grpc"                       % CortexGRPCVersion
    val AkkaPersistenceMongo      = "com.github.scullxbones"        %% "akka-persistence-mongo-scala"      % AkkaPersistenceMongoVersion
    val MongoScalaDriver          = "org.mongodb.scala"             %% "mongo-scala-driver"                % MongoScalaDriverVersion
    val ResourceScheduler         = "deepcortex"                    %% "resource-scheduler"                % ResourceSchedulerVersion

    val All: Seq[ModuleID] = Seq(
      TypeSafeConfig,
      AkkaActor,
      AkkaCluster,
      AkkaZKClusterSeed,
      AkkaHttp,
      AkkaPersistence,
      AkkaClusterSharding,
      AkkaStream,
      PlayJson,
      AkkaHttpPlayJson,
      Cats,
      MarathonClient,
      CortexGRPC,
      AkkaPersistenceMongo,
      MongoScalaDriver,
      ResourceScheduler
    )
  }

  object Test {
    val AkkaTestKit               = "com.typesafe.akka"             %% "akka-testkit"                      % AkkaVersion
    val AkkaHttpTestKit           = "com.typesafe.akka"             %% "akka-http-testkit"                 % AkkaHttpVersion
    val ScalaTest                 = "org.scalatest"                 %% "scalatest"                         % ScalaTestVersion
    val MockitoScala              = "org.mockito"                   %% "mockito-scala"                     % MockitoScalaVersion
    val AkkaPersistenceInMemory   = "com.github.dnvriend"           %% "akka-persistence-inmemory"         % AkkaPersistenceInMemoryVersion

    val All: Seq[ModuleID] = Seq(
      AkkaTestKit,
      AkkaHttpTestKit,
      ScalaTest,
      MockitoScala,
      AkkaPersistenceInMemory
    ).map(_ % Configurations.Test)
  }

}
