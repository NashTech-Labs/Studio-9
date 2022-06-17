package baile.services.usermanagement

import java.util.UUID

import baile.services.asset.AssetService.WithOwnershipTransfer

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }

trait OwnershipTransferRegistry {
  def transferOwnership(from: UUID, to: UUID)(implicit ec: ExecutionContext): Future[Unit]

  def getAllAssetCount(userId: UUID)(implicit ec: ExecutionContext): Future[Int]
}

object OwnershipTransferRegistry extends OwnershipTransferRegistry {
  private val registry = mutable.MutableList[WithOwnershipTransfer[_]]()

  private def register(instance: WithOwnershipTransfer[_]): Unit = this.synchronized {
    registry += instance
  }

  override def transferOwnership(from: UUID, to: UUID)(implicit ec: ExecutionContext): Future[Unit] =
    Future.sequence(registry.map(_.transferOwnership(from, to))).map(_ => ())

  override def getAllAssetCount(userId: UUID)(implicit ec: ExecutionContext): Future[Int] =
    Future.sequence(registry.map(_.getAllAssetCount(userId))).map(_.sum)

  trait Member {
    self: WithOwnershipTransfer[_] =>
    OwnershipTransferRegistry.register(self)
  }
}


