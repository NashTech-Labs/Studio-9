package cortex.testkit

import com.whisk.docker.{ DockerContainer, DockerReadyChecker }

trait DockerRabbitMqService extends DockerKitWithSpotify {

  val image = "rabbitmq:3.6.10-management-alpine"
  val RabbitMqDefaultPort = 5672
  val RabbitMqManagementPluginPort = 15672

  protected val rabbitMqService: DockerContainer = DockerContainer(image)
    .withPorts(
      RabbitMqDefaultPort -> Some(RabbitMqDefaultPort),
      RabbitMqManagementPluginPort -> Some(RabbitMqManagementPluginPort)
    )
    .withReadyChecker(DockerReadyChecker.LogLineContains("Server startup complete"))

  abstract override def containers: Seq[DockerContainer] = super.containers :+ rabbitMqService
}
