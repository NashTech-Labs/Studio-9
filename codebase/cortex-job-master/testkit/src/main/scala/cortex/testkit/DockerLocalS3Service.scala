package cortex.testkit

import com.whisk.docker.{ DockerContainer, DockerReadyChecker }

trait DockerLocalS3Service extends DockerKitWithSpotify {

  private val image = "lphoward/fake-s3:latest"
  protected val fakePort = 4565

  protected val s3DockerContainer: DockerContainer = DockerContainer(image)
    .withPorts(4569 -> Some(fakePort))
    .withReadyChecker(DockerReadyChecker.LogLineContains("start"))

  abstract override def containers: Seq[DockerContainer] = super.containers :+ s3DockerContainer
}
