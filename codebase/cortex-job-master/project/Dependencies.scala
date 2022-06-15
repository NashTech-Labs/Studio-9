import Versions._
import sbt._

object Dependencies {

  // Libraries
  object Compile {
    val jodaTime          = "joda-time"                     %  "joda-time"                          % JodaTimeVersion
    val jodaConvert       = "org.joda"                      %  "joda-convert"                       % JodaConvertVersion
    val PlayJson          = "com.typesafe.play"             %% "play-json"                          % PlayJsonVersion
    val json4s            = "org.json4s"                    %% "json4s-jackson"                     % Json4sVersion
    val json4sExt         = "org.json4s"                    %% "json4s-ext"                         % Json4sVersion
    val json4sPb          = "com.trueaccord.scalapb"        %% "scalapb-json4s"                     % Json4sPbVersion
    val akkaActor         = "com.typesafe.akka"             %% "akka-actor"                         % AkkaVersion
    val akkaSlf4j         = "com.typesafe.akka"             %% "akka-slf4j"                         % AkkaVersion
    val logback           = "ch.qos.logback"                %  "logback-classic"                    % LogbackVersion
    val commonsLang       = "org.apache.commons"            %  "commons-lang3"                      % CommonsLangVersion
    val commonsCodec      = "commons-codec"                 %  "commons-codec"                      % CommonsCodecVersion
    val commonsIo         = "commons-io"                    %  "commons-io"                         % CommonsIoVersion
    val scopt             = "com.github.scopt"              %% "scopt"                              % ScoptVersion

    val mesos             = "org.apache.mesos"              %  "mesos"                              % MesosVersion

    val orionIpcCommon    = "ai.deepcortex.orion"           %% "orion-ipc-common"                   % OrionIpcVersion
    val orionIpcRabbitMq  = "ai.deepcortex.orion"           %% "orion-ipc-rabbitmq"                 % OrionIpcVersion
    val cortexGRPC        = "io.deepcortex"                 %% "cortex-grpc"                        % CortexGrpcVersion
    val httpClient        = "org.apache.httpcomponents"     % "httpclient"                          % HttpClientVersion
    val httpCore          = "org.apache.httpcomponents"     % "httpcore"                            % HttpCoreVersion
    val awsSdkBundle      = "com.amazonaws"                 % "aws-java-sdk-bundle"                 % SdkBundleVersion
    val scalaCsv          = "com.github.tototoshi"          %% "scala-csv"                          % ScalaCsvVersion
  }

  object Test {
    val akkaTestkit               = "com.typesafe.akka"             %% "akka-testkit"                 % AkkaVersion
    val scalaTest                 = "org.scalatest"                 %% "scalatest"                    % ScalaTestVersion
    val scalaCheck                = "org.scalacheck"                %% "scalacheck"                   % ScalaCheckVersion
    val scalaMock                 = "org.scalamock"                 %% "scalamock-scalatest-support"  % ScalaMockVersion
    val scalaMeterCore            = "com.storm-enroute"             %% "scalameter-core"              % ScalaMeterVersion
    val scalaMeter                = "com.storm-enroute"             %% "scalameter"                   % ScalaMeterVersion
    val dockerTestKitScalaTest    = "com.whisk"                     %% "docker-testkit-scalatest"     % DockerTestKitVersion
    val dockerTestKitImplSpotify  = "com.whisk"                     %% "docker-testkit-impl-spotify"  % DockerTestKitVersion
    val psqlConnector             = "org.postgresql"                % "postgresql"                    % PsqlVersion
    val mockito                   = "org.mockito"                   % "mockito-all"                   % MockitoVersion
  }

  val time                    = Seq(Compile.jodaConvert, Compile.jodaTime)
  val json                    = Seq(Compile.PlayJson, Compile.json4s, Compile.json4sExt)
  val akka                    = Seq(Compile.akkaActor)
  val logging                 = Seq(Compile.akkaSlf4j, Compile.logback)
  val apacheCommons           = Seq(Compile.commonsLang, Compile.commonsCodec, Compile.commonsIo)
  val mesos                   = Seq(Compile.mesos)

  val orionRpc = Seq(Compile.orionIpcCommon, Compile.orionIpcRabbitMq)

  lazy val shared = (logging ++ time ++ apacheCommons).map(_ exclude("org.slf4j", "slf4j-log4j12"))

  // Sub-project specific dependencies
  lazy val testkit = shared ++
    Seq(Test.akkaTestkit, Test.scalaTest, Test.scalaMock, Test.mockito, Test.scalaCheck,
      Test.scalaMeterCore, Test.scalaMeter, Test.dockerTestKitScalaTest,
      Test.dockerTestKitImplSpotify
        exclude("org.apache.httpcomponents", "httpclient")
        exclude("org.apache.httpcomponents", "httpcore")) ++ Seq(Compile.httpClient, Compile.httpCore)

  lazy val common = shared ++ json ++ Seq(Test.scalaTest % "test")

  lazy val scheduler = shared ++ mesos ++ json ++ Seq(Test.psqlConnector % "test", Compile.awsSdkBundle)

  lazy val cortexJobMaster = shared ++ orionRpc ++ Seq(Compile.scopt, Compile.awsSdkBundle,
    Compile.cortexGRPC exclude("org.apache.logging.log4j", "log4j-slf4j-impl"), Compile.json4sPb) ++
    Seq(Compile.scalaCsv)
}
