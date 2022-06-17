package baile.routes.contract.common

import baile.domain.common.S3Bucket
import baile.utils.json.EnumReadsBuilder
import com.amazonaws.regions.Regions
import play.api.libs.json._

sealed trait S3BucketReference {
  def toDomain: S3Bucket = this match {
    case S3BucketReference.IdReference(bucketId) => S3Bucket.IdReference(bucketId)
    case item: S3BucketReference.AccessOptions => S3Bucket.AccessOptions(
      region = item.AWSRegion,
      bucketName = item.AWSS3BucketName,
      accessKey = Some(item.AWSAccessKey),
      secretKey = Some(item.AWSSecretKey),
      sessionToken = item.AWSSessionToken
    )
  }
}

object S3BucketReference {

  case class IdReference(bucketId: String) extends S3BucketReference

  case class AccessOptions(
    AWSRegion: String,
    AWSS3BucketName: String,
    AWSAccessKey: String,
    AWSSecretKey: String,
    AWSSessionToken: Option[String]
  ) extends S3BucketReference {
    require(!AWSAccessKey.isEmpty, "AWS Access Key cannot be empty")
    require(!AWSSecretKey.isEmpty, "AWS Secret Key cannot be empty")
  }

  implicit val S3BucketReads: Reads[S3BucketReference] = new Reads[S3BucketReference] {
    override def reads(json: JsValue): JsResult[S3BucketReference] = json \ "AWSS3BucketId" match {
      case JsDefined(JsString(bucketId)) => JsSuccess(IdReference(bucketId))
      case JsDefined(JsNull) | _: JsUndefined => Json.reads[AccessOptions].reads(json)
      case JsDefined(_) => JsError("AWSS3BucketId should be a string")
    }
  }

  implicit def RegionsReads: Reads[Regions] = EnumReadsBuilder.build(
    { case regionName if Regions.values.contains(regionName) => Regions.fromName(regionName) },
    "AWSRegion"
  )

}
