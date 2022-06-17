package cortex.testkit

import com.whisk.docker.scalatest.DockerTestKit
import cortex.io.S3Client
import org.scalatest.Suite

trait WithEmbeddedS3 extends DockerLocalS3Service with DockerTestKit {
  this: Suite =>

  protected var fakeS3Client: S3Client = _
  protected val fakeS3Endpoint = s"http://localhost:$fakePort"
  protected val accessKey = "some_key"
  protected val secretKey = "some_key"
  protected val region = "some_region"

  override def beforeAll(): Unit = {
    super.beforeAll()
    fakeS3Client = new S3Client(accessKey, secretKey, region, endpointUrl = Some(fakeS3Endpoint))
  }
}
