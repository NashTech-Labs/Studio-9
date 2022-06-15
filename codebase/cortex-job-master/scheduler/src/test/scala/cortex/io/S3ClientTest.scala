package cortex.io

import cortex.testkit.WithEmbeddedS3
import org.scalatest.{ FlatSpec, Matchers }

class S3ClientTest extends FlatSpec
  with WithEmbeddedS3
  with Matchers {

  private val bucket = "test_bucket"

  "S3Client" should "put and get Array[Byte] into s3" in {
    val filename = "test_path/test_subpath"
    val payload = "test_payload".getBytes()

    fakeS3Client.put(bucket, filename, payload)

    val fetchedPayload = fakeS3Client.get(bucket, filename)

    payload shouldEqual fetchedPayload
  }

  it should "delete files recursively" in {
    fakeS3Client.put(bucket, "test/target", Array.empty[Byte])
    fakeS3Client.put(bucket, "test/target/sub1", Array.empty[Byte])
    fakeS3Client.put(bucket, "test/target/sub2", Array.empty[Byte])
    fakeS3Client.put(bucket, "test/target1", Array.empty[Byte])
    fakeS3Client.put(bucket, "test/target1/sub1", Array.empty[Byte])
    fakeS3Client.put(bucket, "test/target2", Array.empty[Byte])
    fakeS3Client.deleteRecursively(bucket, "test/target")
    val files = fakeS3Client.getFiles(bucket, Some("test"))
    files.map(_.filepath) shouldBe Seq("test/target1", "test/target1/sub1", "test/target2")
  }
}
