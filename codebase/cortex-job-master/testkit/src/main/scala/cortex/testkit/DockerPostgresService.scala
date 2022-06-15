package cortex.testkit

import com.whisk.docker.{ DockerContainer, DockerReadyChecker }

trait DockerPostgresService extends DockerKitWithSpotify {

  val image = "postgres:9.3"
  val postgresHost = "localhost"
  val postgresPort = 8389
  val postgresDbName, postgresUser, postgresPass = "root"

  protected val postgresDockerContainer: DockerContainer = DockerContainer(image)
    .withPorts(5432 -> Some(postgresPort))
    .withEnv(
      s"POSTGRES_USER=$postgresUser",
      s"POSTGRES_PASSWORD=$postgresPass"
    )
    .withReadyChecker(DockerReadyChecker.LogLineContains("PostgreSQL init process complete"))

  abstract override def containers: Seq[DockerContainer] = super.containers :+ postgresDockerContainer
}
