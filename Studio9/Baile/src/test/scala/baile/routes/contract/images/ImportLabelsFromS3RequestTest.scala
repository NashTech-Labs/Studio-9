package baile.routes.contract.images

import baile.BaseSpec
import baile.routes.contract.common.S3BucketReference
import baile.routes.contract.images.ImportLabelsFromS3Request._
import play.api.libs.json._

class ImportLabelsFromS3RequestTest extends BaseSpec {
  "ImportLabelsFromS3 reads" should {
    "parse json with bucketId" in {

      val json = Json.parse("{\"S3CSVPath\": \"foo\", \"AWSS3BucketId\": \"ddd\"}").as[ImportLabelsFromS3Request]

      json shouldBe an [ImportLabelsFromS3Request]
      json.bucket shouldBe a [S3BucketReference.IdReference]
    }

    "parse json with bucket acces options" in {

      val json = Json.parse(
        """{
        |  "S3CSVPath": "foo",
        |  "AWSRegion": "us-east-1",
        |  "AWSS3BucketName": "dev.deepcortex.ai",
        |  "AWSAccessKey": "xxxxxxx",
        |  "AWSSecretKey": "xxxxxxxxxxxx",
        |  "AWSSessionToken": "xxxxxxxxxxxxxxxxxx"
        |}""".stripMargin).as[ImportLabelsFromS3Request]

      json shouldBe an [ImportLabelsFromS3Request]
      json.bucket shouldBe a [S3BucketReference.AccessOptions]
    }
  }
}
