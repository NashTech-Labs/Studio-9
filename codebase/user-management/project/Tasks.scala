import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerBuildOptions
import sbt._

object Tasks {

  lazy val gitHeadCommitSha = taskKey[String]("Determines the current git commit SHA")

  lazy val gitHeadCommitShaShort = taskKey[String]("Determines the current git commit SHA")

  lazy val makeDockerVersion = taskKey[Seq[File]]("Creates a docker-version.sbt file we can find at runtime.")

  lazy val printClassPath = TaskKey[Unit]("print-class-path")

  lazy val testAll = TaskKey[Unit]("test-all")

  lazy val testForBuild = TaskKey[Unit]("test-for-build")

  def gitHeadCommitShaDef: String = Process("git rev-parse HEAD").lines.head

  def gitHeadCommitShaShortDef: String = Process("git rev-parse --short HEAD").lines.head

  // scalastyle:off regex
  lazy val makeDockerVersionTaskImpl = Def.task {
    val propFile = file(".") / "target/docker-image.version"
    val content = dockerBuildOptions.value(2)
    println(s"Docker-version: $content")
    IO.write(propFile, content)
    Seq(propFile)
  }
}