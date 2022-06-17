import Versions._
import sbt._

object Dependencies {

  // Libraries
  object Compile {
    val akkaHttpJson              = "com.typesafe.akka"     %% "akka-http-spray-json-experimental"  % AkkaVersion
    val akkaActor                 = "com.typesafe.akka"     %% "akka-actor"                         % AkkaVersion
    val logback                   = "ch.qos.logback"        %  "logback-classic"                    % LogbackVersion
    val akkaSlf4j                 = "com.typesafe.akka"     %% "akka-slf4j"                         % AkkaVersion
    val ficus                     = "com.iheart"            %% "ficus"                              % FicusVersion
    val amqpClient                = "com.rabbitmq"          %  "amqp-client"                        % AmqpClientVersion
    val opRabbitCore              = "com.spingo"            %% "op-rabbit-core"                     % OpRabbitVersion
    val opRabbitJson4s            = "com.spingo"            %% "op-rabbit-json4s"                   % OpRabbitVersion
    val json4sJackson             = "org.json4s"            %% "json4s-jackson"                     % Json4sVersion
    val json4sNative              = "org.json4s"            %% "json4s-native"                      % Json4sVersion
  }

  object Test {
    val akkaTestKit               = "com.typesafe.akka"     %% "akka-testkit"                 % AkkaVersion
    val scalaTest                 = "org.scalatest"         %% "scalatest"                    % ScalaTestVersion
    val scalaMock                 = "org.scalamock"         %% "scalamock-scalatest-support"  % ScalaMockVersion
    val scalaMeterCore            = "com.storm-enroute"     %% "scalameter-core"              % ScalaMeterVersion
    val scalaMeter                = "com.storm-enroute"     %% "scalameter"                   % ScalaMeterVersion
    val dockerTestKitScalaTest    = "com.whisk"             %% "docker-testkit-scalatest"     % DockerTestKitVersion
    val dockerTestKitImplSpotify  = "com.whisk"             %% "docker-testkit-impl-spotify"  % DockerTestKitVersion
  }

  import Compile._

  val akka              = Seq(akkaActor)
  val config            = Seq(ficus)
  val logging           = Seq(logback, akkaSlf4j)
  val rabbitMq          = Seq(amqpClient, opRabbitCore, opRabbitJson4s)
  val json4s            = Seq(json4sJackson, json4sNative)

  lazy val shared = logging ++ config

  // Sub-project specific dependencies
  lazy val testKit = shared ++
    Seq(Test.akkaTestKit, Test.scalaTest, Test.scalaMock, Test.scalaMeterCore, Test.scalaMeter,
          Test.dockerTestKitScalaTest, Test.dockerTestKitImplSpotify)

  lazy val common = shared ++ Seq(Test.scalaTest % "test")

  lazy val orionIpcRabbitmq = shared ++ akka ++ rabbitMq ++ json4s
}
