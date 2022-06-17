import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerAlias
import sbt.{ Def, _ }
import sbt.Keys._

object Tasks {

  lazy val makeDockerVersionTaskImpl = Def.task {
    val propFile = file(".") / "target/docker-image.version"
    val content = dockerAlias.value.versioned
    println(s"Docker-version: $content")
    IO.write(propFile, content)
    Seq(propFile)
  }

  lazy val migrateMongoTaskImpl = Def.task {
    (runMain in Compile).toTask(" baile.dao.mongo.migrations.MongoMigrationApp").value
  }

}
