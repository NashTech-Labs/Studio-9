package cortex.jobmaster.orion.service.domain

import java.util.UUID

import cortex.CortexException
import cortex.jobmaster.jobs.job.image_uploading.ImageFile
import cortex.jobmaster.jobs.job.image_uploading.ImageFilesSource.{ S3FilesSequence, S3FilesSource }
import cortex.task.StorageAccessParams.S3AccessParams
import org.scalatest.Matchers._
import org.scalatest.WordSpec

class ImageUploadingBaseTest extends WordSpec {

  lazy val albumPath: String = s"cortex-job-master/e2e/test_albums/test_album-${UUID.randomUUID().toString}"

  var modelId: String = _
  val baseImagesPath = "cortex-job-master/e2e/img_sample"

  lazy val s3AccessParams = S3AccessParams(
    bucket      = "baseBucket",
    accessKey   = "accessKey",
    secretKey   = "secretKey",
    region      = "",
    endpointUrl = Some("fakeS3Endpoint")
  )
  val uploadingServiceBase: ImageUploadingBase = new ImageUploadingBase {}

  "prepareParams" should {
    "prepare params for upload" in {
      val params = uploadingServiceBase.prepareParams(
        s3AccessParams,
        albumPath,
        imagesPath = Some(baseImagesPath)
      )

      params.albumPath shouldBe albumPath
      params.inS3AccessParams shouldBe s3AccessParams
    }

    "throw when no source fo files specified" in {
      intercept[CortexException] {
        uploadingServiceBase.prepareParams(
          s3AccessParams,
          albumPath,
          imagesPath = None,
          imageFiles = None
        )
      }
    }

    "use S3FilesSource when no particular files given" in {
      val params = uploadingServiceBase.prepareParams(
        s3AccessParams,
        albumPath,
        imagesPath = Some(baseImagesPath),
        imageFiles = None
      )

      params.imageFilesSource.isInstanceOf[S3FilesSource] shouldBe true
      params.imageFilesSource.baseRelativePath shouldBe Some(baseImagesPath)
    }

    "use S3FilesSequence when files given" in {
      val params = uploadingServiceBase.prepareParams(
        s3AccessParams,
        albumPath,
        imagesPath = None,
        imageFiles = Some(Seq(ImageFile("foo", 1L, None)))
      )

      params.imageFilesSource.isInstanceOf[S3FilesSequence] shouldBe true
      params.imageFilesSource.getFiles.length shouldBe 1
      params.imageFilesSource.baseRelativePath shouldBe None
    }

    "use propagate imagesPath when given with files list" in {
      val params = uploadingServiceBase.prepareParams(
        s3AccessParams,
        albumPath,
        imagesPath = Some(baseImagesPath),
        imageFiles = Some(Seq(ImageFile("foo", 1L, None)))
      )

      params.imageFilesSource.isInstanceOf[S3FilesSequence] shouldBe true
      params.imageFilesSource.getFiles.length shouldBe 1
      params.imageFilesSource.baseRelativePath shouldBe Some(baseImagesPath)
    }

    "convert empty labelsPath to None" in {
      val csvPath = ""

      val params = uploadingServiceBase.prepareParams(
        s3AccessParams,
        albumPath,
        imagesPath    = Some(baseImagesPath),
        labelsCSVPath = csvPath
      )

      params.csvFileS3Path shouldBe None
    }

    "accept non-empty labelsPath" in {
      val params = uploadingServiceBase.prepareParams(
        s3AccessParams,
        albumPath,
        imagesPath    = Some(baseImagesPath),
        labelsCSVPath = "path/to/csv"
      )

      params.csvFileS3Path shouldBe Some("path/to/csv")
    }

    "convert empty labels bytes to None" in {
      val params = uploadingServiceBase.prepareParams(
        s3AccessParams,
        albumPath,
        imagesPath    = Some(baseImagesPath),
        labelsCSVFile = Array.emptyByteArray
      )

      params.csvFileBytes shouldBe None
    }

    "accept non-empty labels bytes" in {
      val params = uploadingServiceBase.prepareParams(
        s3AccessParams,
        albumPath,
        imagesPath    = Some(baseImagesPath),
        labelsCSVFile = Array[Byte](1)
      )

      params.csvFileBytes.isInstanceOf[Some[Array[Byte]]] shouldBe true
    }

    "strip trailing slashes on imagesPath" in {
      val params1 = uploadingServiceBase.prepareParams(
        s3AccessParams,
        albumPath,
        imagesPath = Some(s"$baseImagesPath/"),
        imageFiles = Some(Seq(ImageFile("foo", 1L, None)))
      )

      val params2 = uploadingServiceBase.prepareParams(
        s3AccessParams,
        albumPath,
        imagesPath = Some(s"$baseImagesPath/"),
        imageFiles = None
      )

      params1.imageFilesSource.baseRelativePath shouldBe Some(baseImagesPath)
      params2.imageFilesSource.baseRelativePath shouldBe Some(baseImagesPath)
    }
  }
}
