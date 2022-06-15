package cortex.testkit

import cortex.rpc.S3TaskRPC
import org.scalatest.Suite

trait WithS3TaskRPC extends WithTaskRPC with WithEmbeddedS3 {
  this: Suite =>

  var taskRPC: S3TaskRPC = _

  val baseBucket: String = "test-bucket"
  val basePath: String = "path"

  abstract override def beforeAll(): Unit = {
    super.beforeAll()
    taskRPC = new S3TaskRPC(baseBucket, basePath, fakeS3Client)
  }
}
