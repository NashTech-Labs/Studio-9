package cortex.testkit

import com.spotify.docker.client.{ DefaultDockerClient, DockerClient }
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.{ DockerContainer, DockerFactory, DockerKit }

trait DockerKitWithSpotify extends DockerKit {

  def containers: Seq[DockerContainer] = Seq.empty

  abstract override def dockerContainers: List[DockerContainer] = containers.toList ++ super.dockerContainers

  private val client: DockerClient = DefaultDockerClient.fromEnv().build()
  override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(client)
}
