package orion.ipc.rabbitmq.setup

import org.scalamock.scalatest.MockFactory
import orion.ipc.rabbitmq.DockerRabbitMqServiceTestSpec
import orion.ipc.rabbitmq.setup.builders.Builder

class ClusterTest extends DockerRabbitMqServiceTestSpec with MockFactory {

  private val topologyBuilderMock = mock[Builder]

  "Cluster" should {

    "initialize a builder" in {
      (topologyBuilderMock.name _).expects().anyNumberOfTimes()
      (topologyBuilderMock.build _).expects(*).once()

      noException should be thrownBy Cluster(topologyBuilderMock).initialize()
    }

    "initialize a sequance of builders" in {
      (topologyBuilderMock.name _).expects().anyNumberOfTimes()
      (topologyBuilderMock.build _).expects(*).twice()

      noException should be thrownBy Cluster(topologyBuilderMock, topologyBuilderMock).initialize()
    }
  }
}
