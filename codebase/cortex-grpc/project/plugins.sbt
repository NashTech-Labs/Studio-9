addSbtPlugin("com.lucidchart"     %  "sbt-scalafmt"           % "1.3")
addSbtPlugin("com.thesamet"       %  "sbt-protoc"             % "0.99.11")
addSbtPlugin("com.typesafe.sbt"   %  "sbt-git"                % "0.9.3")
addSbtPlugin("org.scalastyle"     %% "scalastyle-sbt-plugin"  % "0.8.0")

addSbtPlugin("com.github.gseitz"  %  "sbt-release"            % "1.0.5")
addSbtPlugin("com.frugalmechanic" %  "fm-sbt-s3-resolver"     % "0.11.0")
addSbtPlugin("net.virtual-void"   %  "sbt-dependency-graph"   % "0.8.2")

libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.0"
