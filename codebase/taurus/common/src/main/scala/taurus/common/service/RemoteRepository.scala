package taurus.common.service

import akka.actor.ActorSystem

import scala.concurrent.Future

trait RemoteRepository {
  import RemoteRepository._

  def write(path: String, content: Array[Byte]): Future[WriteResult]

  def read(path: String): Future[Array[Byte]]

}

object RemoteRepository {
  sealed trait WriteResult
  case object WriteResult extends WriteResult

  def s3Repository(actorSystem: ActorSystem): RemoteRepository = {
    S3Client(actorSystem)
  }

}

// Actor support
trait RemoteRepositorySupport { self: Service =>
  import RemoteRepository._

  val remoteRepository: RemoteRepository

  def write(path: String, content: Array[Byte]): Future[WriteResult] = remoteRepository.write(path, content)

  def read(path: String): Future[Array[Byte]] = remoteRepository.read(path)

}
