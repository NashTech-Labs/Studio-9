package taurus.testkit.service

import com.spotify.docker.client.{ DefaultDockerClient, DockerClient }
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.{ DockerContainer, DockerFactory, DockerKit }

trait DockerKitWithSpotify extends DockerKit {

  def container: DockerContainer

  abstract override def dockerContainers: List[DockerContainer] = container :: super.dockerContainers

  private val client: DockerClient = DefaultDockerClient.fromEnv().build()
  override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(client)
}
