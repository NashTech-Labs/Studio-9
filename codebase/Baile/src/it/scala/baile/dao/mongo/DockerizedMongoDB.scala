package baile.dao.mongo

import com.whisk.docker.{ DockerContainer, DockerKit, DockerReadyChecker }
import org.mongodb.scala.{ MongoClient, MongoDatabase }
import org.scalatest.concurrent.ScalaFutures

trait DockerizedMongoDB extends DockerKit { self: ScalaFutures =>

  val defaultMongodbPort = 27017

  val mongodbContainer = DockerContainer("mongo:3.4.14")
    .withPorts(defaultMongodbPort -> None)
    .withReadyChecker(DockerReadyChecker.LogLineContains("waiting for connections on port"))
    .withCommand("mongod", "--nojournal", "--smallfiles", "--syncdelay", "0")

  protected lazy val allocatedMongoPort: Int = mongodbContainer.getPorts().futureValue.apply(defaultMongodbPort)
  protected lazy val mongoClient: MongoClient = MongoClient(s"mongodb://localhost:$allocatedMongoPort")
  protected lazy val database: MongoDatabase = mongoClient.getDatabase("test-db")

  abstract override def dockerContainers: List[DockerContainer] =
    mongodbContainer :: super.dockerContainers

}
