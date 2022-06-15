package cortex.api.job.pegasus

import java.time.ZonedDateTime

import cortex.api.BaseJson4sFormats
import cortex.api.pegasus.{CreatedBy, PegasusJobStatus, PredictionImportRequest, PredictionImportResponse}
import org.json4s.Extraction
import org.json4s.jackson.{compactJson, parseJson}
import org.scalatest.{FreeSpec, Matchers}


class PegasusContractSerializationSpec extends FreeSpec with Matchers {
  implicit val formats = BaseJson4sFormats.extend(
    PredictionImportRequest.CreatedByFormats,
    PredictionImportResponse.PegasusJobStatusFormats
  )

  "Pegasus contract" - {

    "PredictionImportRequest" - {
      val predictionImportRequestStr =
        """{
          |"jobId":"jobId",
          |"streamId":"streamId",
          |"albumId":"albumId",
          |"owner":"ownerId",
          |"createdAt":"2018-02-16T08:03:28.989Z",
          |"createdBy":"Taurus",
          |"s3PredictionCsvPath":"s3://bucket/some/relative/path"}""".stripMargin.replace("\n", "")

      "should be serializable" in {
        val predictionImportRequest = PredictionImportRequest(
          jobId = "jobId",
          streamId = "streamId",
          albumId = "albumId",
          owner = "ownerId",
          createdAt = ZonedDateTime.parse("2018-02-16T08:03:28.989Z"),
          createdBy = CreatedBy.Taurus,
          s3PredictionCsvPath = "s3://bucket/some/relative/path"
        )

        val jsonStr = compactJson(Extraction.decompose(predictionImportRequest))
        jsonStr shouldBe predictionImportRequestStr
      }

      "should be deserializable" in {
        val obj = Extraction.extract[PredictionImportRequest](parseJson(predictionImportRequestStr))
        obj.jobId shouldBe "jobId"
      }
    }

    "PredictionImportResponse" - {
      val predictionImportResponseStr = """{"jobId":"jobId","pegasusJobStatus":"Succeed"}"""

      "should be serializable" in {
        val predictionImportResponse = PredictionImportResponse(
          jobId = "jobId",
          pegasusJobStatus = PegasusJobStatus.Succeed
        )
        val jsonStr = compactJson(Extraction.decompose(predictionImportResponse))
        jsonStr shouldBe predictionImportResponseStr
      }

      "should be deserializable" in {
        val obj = Extraction.extract[PredictionImportResponse](parseJson(predictionImportResponseStr))
        obj.jobId shouldBe "jobId"
        obj.pegasusJobStatus shouldBe PegasusJobStatus.Succeed
      }
    }

  }
}
