import Versions._
import sbt._

object Dependencies {

  // Libraries
  object Compile {
    val jodaTime          = "joda-time"                     %  "joda-time"                          % JodaTimeVersion
    val jodaConvert       = "org.joda"                      %  "joda-convert"                       % JodaConvertVersion
    val json4s            = "org.json4s"                    %% "json4s-jackson"                     % Json4sVersion
    val json4sExt         = "org.json4s"                    %% "json4s-ext"                         % Json4sVersion
    val akkaActor         = "com.typesafe.akka"             %% "akka-actor"                         % AkkaVersion
    val akkaStream        = "com.typesafe.akka"             %% "akka-stream"                        % AkkaVersion
    val akkaHttp          = "com.typesafe.akka"             %% "akka-http"                          % AkkaHttpVersion
    val ficus             = "com.iheart"                    %% "ficus"                              % FicusVersion
    val akkaSlf4j         = "com.typesafe.akka"             %% "akka-slf4j"                         % AkkaVersion
    val logback           = "ch.qos.logback"                %  "logback-classic"                    % LogbackVersion
    val commonsLang       = "org.apache.commons"            %  "commons-lang3"                      % CommonsLangVersion
    val commonsCodec      = "commons-codec"                 %  "commons-codec"                      % CommonsCodecVersion
    val commonsIo         = "commons-io"                    %  "commons-io"                         % CommonsIoVersion
    val elastic4score     = "com.sksamuel.elastic4s"        %% "elastic4s-core"                     % Elastic4sVersion
    val elastic4shttp     = "com.sksamuel.elastic4s"        %% "elastic4s-http"                     % Elastic4sVersion
    val elastic4sJson4s   = "com.sksamuel.elastic4s"        %% "elastic4s-json4s"                   % Elastic4sVersion
  }

  object Test {
    val akkaTestkit       = "com.typesafe.akka"             %% "akka-testkit"                 % AkkaVersion
    val akkaHttpTestkit   = "com.typesafe.akka"             %% "akka-http-testkit"            % AkkaHttpVersion
    val scalaTest         = "org.scalatest"                 %% "scalatest"                    % ScalaTestVersion
    val scalaMock         = "org.scalamock"                 %% "scalamock-scalatest-support"  % ScalaMockVersion
    val fabricator        = "com.github.azakordonets"       %% "fabricator"                   % FabricatorTestVersion
    val scalaMeterCore    = "com.storm-enroute"             %% "scalameter-core"              % ScalaMeterVersion
    val scalaMeter        = "com.storm-enroute"             %% "scalameter"                   % ScalaMeterVersion
    val groovy            = "org.codehaus.groovy"           %  "groovy-all"                   % GroovyVersion
    val elastic4stest     = "com.sksamuel.elastic4s"        %% "elastic4s-testkit"            % Elastic4sVersion
  }

  val time              = Seq(Compile.jodaConvert, Compile.jodaTime)
  val json4sJson        = Seq(Compile.json4s, Compile.json4sExt)
  val akka              = Seq(Compile.akkaActor, Compile.akkaStream)
  val akkaHttp          = Seq(Compile.akkaHttp, Compile.akkaStream) ++ json4sJson
  val config            = Seq(Compile.ficus)
  val logging           = Seq(Compile.akkaSlf4j, Compile.logback)
  val apacheCommons     = Seq(Compile.commonsLang, Compile.commonsCodec, Compile.commonsIo)
  val elastic4s         = Seq(Compile.elastic4score, Compile.elastic4shttp, Compile.elastic4sJson4s)

  lazy val shared = logging ++ time ++ apacheCommons ++ config

  // Sub-project specific dependencies
  lazy val testkit = shared ++
    Seq(Test.akkaTestkit, Test.akkaHttpTestkit, Test.scalaTest, Test.scalaMock,
      Test.groovy, Test.fabricator, Test.scalaMeterCore, Test.scalaMeter, Test.elastic4stest)

  lazy val common = shared ++ akkaHttp ++ json4sJson ++ Seq(Test.scalaTest % "test")

  lazy val argoApiService = shared ++ akkaHttp ++ elastic4s

  lazy val argoApiRest = shared ++ akka
}