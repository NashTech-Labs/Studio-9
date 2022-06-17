import Versions._
import sbt._

object Dependencies {

  // Libraries
  object Compile {
    val jodaTime             = "joda-time"                     %  "joda-time"                          % JodaTimeVersion
    val jodaConvert          = "org.joda"                      %  "joda-convert"                       % JodaConvertVersion
    val json4s               = "org.json4s"                    %% "json4s-jackson"                     % Json4sVersion
    val json4sExt            = "org.json4s"                    %% "json4s-ext"                         % Json4sVersion
    val akkaActor            = "com.typesafe.akka"             %% "akka-actor"                         % AkkaVersion
    val akkaCluster          = "com.typesafe.akka"             %% "akka-cluster"                       % AkkaVersion
    val akkaZKClusterSeed    = "com.sclasen"                   %% "akka-zk-cluster-seed"               % AkkaZKClusterSeedVersion
    val akkaClusterSharding  = "com.typesafe.akka"             %% "akka-cluster-sharding"              % AkkaVersion
    val akkaPersistence      = "com.typesafe.akka"             %% "akka-persistence"                   % AkkaVersion
    val akkaStream           = "com.typesafe.akka"             %% "akka-stream"                        % AkkaVersion
    val akkaHttp             = "com.typesafe.akka"             %% "akka-http"                          % AkkaHttpVersion
    val ficus                = "com.iheart"                    %% "ficus"                              % FicusVersion
    val akkaSlf4j            = "com.typesafe.akka"             %% "akka-slf4j"                         % AkkaVersion
    val logback              = "ch.qos.logback"                %  "logback-classic"                    % LogbackVersion
    val commonsLang          = "org.apache.commons"            %  "commons-lang3"                      % CommonsLangVersion
    val commonsCodec         = "commons-codec"                 %  "commons-codec"                      % CommonsCodecVersion
    val commonsIo            = "commons-io"                    %  "commons-io"                         % CommonsIoVersion
    val orionIpcRabbitMq     = "ai.deepcortex.orion"           %% "orion-ipc-rabbitmq"                 % OrionIpcVersion
    val orionIpcCommon       = "ai.deepcortex.orion"           %% "orion-ipc-common"                   % OrionIpcVersion
    val marathonClient       = "com.mesosphere"                %  "marathon-client"                    % MarathonClientVersion
    val akkaPersistenceMongo = "com.github.scullxbones"        %% "akka-persistence-mongo-scala"       % AkkaPersistenceMongoVersion
    val mongoScalaDriver     = "org.mongodb.scala"             %% "mongo-scala-driver"                 % MongoScalaDriverVersion
    val cortexGRPC           = "io.deepcortex"                 %% "cortex-grpc"                        % CortexGRPCVersion
  }

  object Test {
    val akkaTestkit               = "com.typesafe.akka"             %% "akka-testkit"                 % AkkaVersion
    val akkaHttpTestkit           = "com.typesafe.akka"             %% "akka-http-testkit"            % AkkaHttpVersion
    val levelDb                   = "org.iq80.leveldb"              %  "leveldb"                      % LevelDbVersion
    val levelDbJni                = "org.fusesource.leveldbjni"     %  "leveldbjni-all"               % LevelDbJniVersion
    val scalaTest                 = "org.scalatest"                 %% "scalatest"                    % ScalaTestVersion
    val scalaMock                 = "org.scalamock"                 %% "scalamock-scalatest-support"  % ScalaMockVersion
    val fabricator                = "com.github.azakordonets"       %% "fabricator"                   % FabricatorTestVersion
    val scalaMeterCore            = "com.storm-enroute"             %% "scalameter-core"              % ScalaMeterVersion
    val scalaMeter                = "com.storm-enroute"             %% "scalameter"                   % ScalaMeterVersion
    val groovy                    = "org.codehaus.groovy"           %  "groovy-all"                   % GroovyVersion
    val dockerTestKitScalaTest    = "com.whisk"                     %% "docker-testkit-scalatest"     % DockerTestKitVersion
    val dockerTestKitImplSpotify  = "com.whisk"                     %% "docker-testkit-impl-spotify"  % DockerTestKitVersion
  }

  val time              = Seq(Compile.jodaConvert, Compile.jodaTime)
  val json4sJson        = Seq(Compile.json4s, Compile.json4sExt)
  val akka              = Seq(Compile.akkaActor)
  val akkaPersistence   = Seq(Compile.akkaPersistence, Compile.akkaPersistenceMongo, Compile.mongoScalaDriver)
  val akkaHttp          = Seq(Compile.akkaHttp, Compile.akkaStream) ++ json4sJson
  val akkaCluster       = Seq(Compile.akkaCluster, Compile.akkaZKClusterSeed, Compile.akkaClusterSharding)
  val config            = Seq(Compile.ficus)
  val logging           = Seq(Compile.akkaSlf4j, Compile.logback)
  val apacheCommons     = Seq(Compile.commonsLang, Compile.commonsCodec, Compile.commonsIo)
  val orionRpc          = Seq(Compile.orionIpcCommon, Compile.orionIpcRabbitMq)
  val marathon          = Seq(Compile.marathonClient)
  val cortexGRPC        = Seq(Compile.cortexGRPC)

  lazy val shared = logging ++ time ++ apacheCommons ++ config

  // Sub-project specific dependencies
  lazy val testkit = shared ++
    Seq(Test.akkaTestkit, Test.akkaHttpTestkit, Test.levelDb, Test.levelDbJni, Test.scalaTest,
      Test.scalaMock, Test.dockerTestKitScalaTest, Test.dockerTestKitImplSpotify)

  lazy val common = shared ++ akkaCluster ++ akkaPersistence ++ akkaHttp ++ json4sJson ++ orionRpc ++ marathon ++ Seq(Test.scalaTest % "test")

  lazy val orionApiService = shared ++ akkaCluster ++ akkaPersistence ++ cortexGRPC

  lazy val orionApiRest = shared ++ akkaHttp
}