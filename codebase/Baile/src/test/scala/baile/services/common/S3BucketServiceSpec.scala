package baile.services.common

import baile.BaseSpec
import baile.domain.common.S3Bucket.{ AccessOptions, IdReference }
import baile.services.common.S3BucketService.{ BucketNotFound, EmptyKey, InvalidAWSRegion }
import cats.implicits._

import scala.collection.JavaConverters._
import scala.concurrent.Future

class S3BucketServiceSpec extends BaseSpec {

  val service = new S3BucketService(conf)
  val bucketsFromConfig = conf
    .getConfigList("aws.predefined-buckets")
    .asScala
    .toList
    .map { elem =>
      elem.getString("id") -> elem.getString("name")
    }

  assert(bucketsFromConfig.nonEmpty, "no buckets from config for test fixtures")

  "S3BucketService#listAll" should {
    "list access options for all buckets from config" in {
      whenReady(service.listAll()) { accessOptions =>
        accessOptions.map(_.id) should contain allElementsOf bucketsFromConfig.map { case (id, _) => id }
      }
    }
  }

  "S3BucketService#dereferenceBucket" should {

    "return bucket access options for id" in {
      val ids = bucketsFromConfig.map { case (id, _) => IdReference(id) }
      whenReady(Future.sequence(ids.map(service.dereferenceBucket))) { results =>
        type ErrorOr[T] = Either[_, T]
        results
          .sequence[ErrorOr, AccessOptions]
          .right
          .get
          .map(_.bucketName) should contain allElementsOf bucketsFromConfig.map { case (_, name) => name }
      }
    }

    "return error when there is no bucket with provided id" in {
      whenReady(service.dereferenceBucket(IdReference("42"))) {
        _ shouldBe BucketNotFound.asLeft
      }
    }

    "return error when given access options with empty keys" in {
      whenReady(service.dereferenceBucket(AccessOptions("us-east-1", "bucket", Some(""), Some(""), None))) {
        _ shouldBe EmptyKey.asLeft
      }
    }

    "return error when given access options with none key(s)" in {
      whenReady(service.dereferenceBucket(AccessOptions("us-east-1", "bucket", None, Some("secret"), None))) {
        _ shouldBe EmptyKey.asLeft
      }
    }

    "return error when given access options with bad region" in {
      whenReady(service.dereferenceBucket(AccessOptions("washington", "bucket", Some("key"), Some("secret"), None))) {
        _ shouldBe InvalidAWSRegion.asLeft
      }
    }

  }

}
