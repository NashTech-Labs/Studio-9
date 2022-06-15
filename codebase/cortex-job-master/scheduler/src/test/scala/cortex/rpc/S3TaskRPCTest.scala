package cortex.rpc

import java.nio.charset.Charset
import java.util.UUID

import cortex.TaskParams
import cortex.io.S3Client
import cortex.testkit.WithS3TaskRPC
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ FlatSpec, Matchers }

class S3TaskRPCTest extends FlatSpec
  with WithS3TaskRPC
  with MockFactory
  with Matchers {

  val taskId: String = UUID.randomUUID().toString
  val sampleParams: Array[Byte] = "[\"fake_string\", 1]".getBytes(Charset.forName("UTF-8"))
  val sampleResult = "[\"fake_string\", 1]"

  "S3TaskRPC" should "store task parameters in s3" in {
    val s3client = mock[S3Client]
    (s3client.put _).expects("base_bucket", *, sampleParams)

    taskRPC = new S3TaskRPC("base_bucket", "", s3client)

    taskRPC.passParameters(taskId, sampleParams)
  }

  it should "read task result from s3 without any transformations" in {
    val resultBytes = sampleResult.getBytes(Charset.forName("UTF-8"))

    val s3client = stub[S3Client]
    taskRPC = new S3TaskRPC("base_bucket", "", s3client)

    (s3client.get(_: String, _: String)).when("base_bucket", taskRPC.buildResultEndpoint(taskId)).returns(resultBytes)

    taskRPC.getResults(taskId) shouldEqual resultBytes
  }

}
