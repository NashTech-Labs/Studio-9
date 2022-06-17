package cortex.jobmaster.orion.service.io

import java.util.UUID

import com.trueaccord.scalapb.GeneratedMessage
import cortex.JsonSupport
import cortex.api.job.common.ClassReference
import cortex.api.job.album.common.{ Image, Tag, TaggedImage }
import cortex.api.job.computervision._
import cortex.testkit.{ BaseSpec, WithEmbeddedS3, WithLogging }
import play.api.libs.json.OFormat

class S3ParamResultStorageTest extends BaseSpec with WithEmbeddedS3 with WithLogging {
  case class SampleClass(
      sampleField1: Int,
      sampleField2: String,
      sampleField3: (Int, String),
      sampleField4: Seq[Int],
      sampleField5: Option[SampleClass]
  )

  private implicit val sampleClassFormat: OFormat[SampleClass] = JsonSupport.SnakeJson.format[SampleClass]

  val baseBucket = "local.deepcortex/tmp/jobs"

  val inputRequest = CVModelTrainRequest(
    featureExtractorId = Some("test-job-id"),
    images             = Seq(
      TaggedImage(Some(Image("1.png")), Seq(Tag("1", None))),
      TaggedImage(Some(Image("2.png")), Seq(Tag("2", None)))
    ),
    modelType          = Some(TLModelType(TLModelType.Type.ClassifierType(
      ClassReference(None, "ml_lib.classifiers", "FCN1")
    )))
  )

  val sampleInstance = SampleClass(
    3,
    "foo",
    (42, "bar"),
    Seq(1, 2, 3),
    Some(SampleClass(
      2,
      "quix",
      (24, "quz"),
      Seq(43),
      None
    ))
  )

  "S3ParamResultStorage Reader and Writer" should {

    "perform put/get operations for a service message type (example)[1]" in {
      val factory = new S3ParamResultStorageFactory(fakeS3Client, baseBucket, "")
      val writer = factory.createParamResultStorageWriter[GeneratedMessage]()
      val reader = factory.createParamResultStorageReader[CVModelTrainRequest]()

      val path = writer.put(inputRequest, UUID.randomUUID().toString)
      val restoredRequest = reader.get(path)
      inputRequest shouldBe restoredRequest
    }

    "perform put/get operations for a service message type (example)[2]" in {
      val factory = new S3ParamResultStorageFactory(fakeS3Client, baseBucket, "")
      val writer = factory.createStorageWriter[SampleClass]()
      val reader = factory.createStorageReader[SampleClass]()

      val path = writer.put(sampleInstance, s"${UUID.randomUUID().toString}/test")
      val restoredInstance = reader.get(path)
      sampleInstance shouldBe restoredInstance
    }
  }

}
