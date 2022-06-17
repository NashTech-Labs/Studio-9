package baile.services.asset

import java.time.Instant
import java.util.UUID

import baile.domain.asset.{ Asset, AssetStatus }
import baile.services.asset.SampleAsset.SampleAssetStatus

case class SampleAsset(
  bar: String,
  baz: Int,

  ownerId: UUID = UUID.randomUUID,
  name: String = "foo",
  created: Instant = Instant.now(),
  updated: Instant = Instant.now(),
  status: SampleAssetStatus = SampleAssetStatus.OK,
  inLibrary: Boolean = true,
  description: Option[String] = None

) extends Asset[SampleAssetStatus]

object SampleAsset {

  sealed trait SampleAssetStatus extends AssetStatus

  object SampleAssetStatus {
    case object OK extends SampleAssetStatus
  }

}
