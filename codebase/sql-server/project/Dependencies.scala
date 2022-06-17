import Versions._
import sbt._

object Dependencies {

  object Compile {
    val TypeSafeConfig         = "com.typesafe"                  %  "config"                            % TypeSafeConfigVersion
    val AkkaActor              = "com.typesafe.akka"             %% "akka-actor"                        % AkkaVersion
    val AkkaHttp               = "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion
    val AkkaStream             = "com.typesafe.akka"             %% "akka-stream"                       % AkkaVersion
    val PlayJson               = "com.typesafe.play"             %% "play-json"                         % PlayJsonVersion
    val AkkaHttpPlayJson       = "de.heikoseeberger"             %% "akka-http-play-json"               % AkkaHttpPlayJsonVersion
    val Cats                   = "org.typelevel"                 %% "cats-core"                         % CatsVersion
    val RedshiftJDBC           = "com.amazon.redshift"           %  "redshift-jdbc42"                   % RedshiftJDBCVersion
    val JSqlParser             = "com.github.jsqlparser"         %  "jsqlparser"                        % JSqlParserVersion
    val CortexGRPC             = "io.deepcortex"                 %% "cortex-grpc"                       % CortexGRPCVersion
    val AlpakkaCSV             = "com.lightbend.akka"            %% "akka-stream-alpakka-csv"           % AlpakkaVersion

    val All: Seq[ModuleID] = Seq(
      TypeSafeConfig,
      AkkaActor,
      AkkaHttp,
      AkkaStream,
      PlayJson,
      AkkaHttpPlayJson,
      Cats,
      RedshiftJDBC,
      JSqlParser,
      CortexGRPC,
      AlpakkaCSV
    )
  }

  object Test {
    val AkkaTestKit               = "com.typesafe.akka"             %% "akka-testkit"             % AkkaVersion
    val AkkaHttpTestKit           = "com.typesafe.akka"             %% "akka-http-testkit"        % AkkaHttpVersion
    val ScalaTest                 = "org.scalatest"                 %% "scalatest"                % ScalaTestVersion
    val MockitoScala              = "org.mockito"                   %% "mockito-scala"            % MockitoScalaVersion

    val All: Seq[ModuleID] = Seq(
      AkkaTestKit,
      AkkaHttpTestKit,
      ScalaTest,
      MockitoScala
    ).map(_ % Configurations.Test)
  }

}
