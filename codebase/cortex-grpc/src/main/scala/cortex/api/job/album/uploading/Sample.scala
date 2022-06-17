package cortex.api.job.album.uploading

import java.nio.file.Files
import java.util.UUID

import com.google.protobuf.ByteString
import cortex.api.job.common.{ FailedFile, File }
import cortex.api.job.album.common.Tag

// scalastyle:off
object Sample {

  object Images {

    val request = S3ImagesImportRequest(
      bucketName = "dev.deepcortex.ai",
      awsRegion = "us-east-1",
      awsAccessKey = "ASIAJPAVS26YD7IALWMA",
      awsSecretKey = "DAIJ3Jk57m7wWkKLj+IGqhryoYx3+JN7NZoJoVXj",
      awsSessionToken =
        "FQoDYXdzEND//////////wEaDEPIC8JXf96C8rkAxSKsAdyBBJAr0ZWX4g/DzJwMlXYLXjuiZZq2kzILauD" +
          "JssHtOd8BhV3K98GJzLRPyGZoW/dBNpRRSC+jIw4PBtTV51DNYGHPBqeP3LoXqo8Jq4tKE0eemFcZ1+mXauSSNIDEJJJ++cFH+6" +
          "vG0AjfjotAMFpOhs8pu5y9VKm+WkesndrDLIpPpVtXvSgtYZV+kAE8tFAutxWjUSEYxgnocaxlrr5jborKFSJfxnkmqcso8PfJ0AU=",
      imagesPath = "MSTAR_Data/Train_Data/2S1/",
      labelsCsvPath = "MSTAR_Data/Train_Data/2S1/labels.csv",
      labelsCsvFile = getFileContent(new java.io.File("sample.csv")),
      targetPrefix = s"baile/data/albums/${UUID.randomUUID.toString}",
      labelMode = AlbumLabelMode.CLASSIFICATION,
      applyLogTransformation = true
    )

    private def getFileContent(file: java.io.File): ByteString = {
      val fileContent = Files.readAllBytes(file.toPath)
      ByteString.copyFrom(fileContent)
    }

    val successfulImages = Seq(
      UploadedImage(
        file = Some(File(
          filePath = "HB19528.000",
          fileSize = 20,
          fileName = "HB19528.000.png"
        )),
        tags = Seq(Tag("label1")),
        metadata = Map(
          "location" -> "-10.023978989477996,33.437548033986744,6.861708891866691",
          "azimuthAngle" -> "-15.553092151830981",
          "scaleFactor" -> "20.166520149611195",
          "originalImageChipUrl" -> "foo",
          "chipUniqueIdentifier" -> "chip0",
          "dateTime" -> "2017-11-13T16:05:27.800Z",
          "layoverAngle" -> "-36.83274257891712",
          "grazingAngle" -> "79.55534001547893",
          "sampleSpacing" -> "11.373227032872343"
        )
      ),
      UploadedImage(
        file = Some(File(
          filePath = "HB19526.000",
          fileSize = 30,
          fileName = "HB19526.000.png"
        )),
        tags = Seq(Tag("label1")),
        metadata = Map(
          "location" -> "-10.023978989477996,33.437548033986744,6.861708891866691",
          "azimuthAngle" -> "-15.553092151830981",
          "scaleFactor" -> "20.166520149611195",
          "originalImageChipUrl" -> "foo",
          "chipUniqueIdentifier" -> "chip0",
          "label" -> "s3://example/chip0",
          "dateTime" -> "2017-11-13T16:05:27.800Z",
          "layoverAngle" -> "-36.83274257891712",
          "grazingAngle" -> "79.55534001547893",
          "sampleSpacing" -> "11.373227032872343"
        )
      ),
      UploadedImage(
        file = Some(File(
          filePath = "subdir/HB19522.000",
          fileSize = 40,
          fileName = "subdir/HB19522.000.png"
        )),
        tags = Seq(Tag("label2")),
        metadata = Map()
      )
    )

    val failedFiles = Seq(
      FailedFile(
        filePath = "HB19528.000",
        errorMessage = Some("Bad SAR header")
      )
    )

    val result = S3ImagesImportResult(
      images = successfulImages,
      failedFiles = failedFiles
    )

  }

  object Video {

    val request = S3VideoImportRequest(
      bucketName = "dev.deepcortex.ai",
      awsRegion = "us-east-1",
      awsAccessKey = "ASIAJPAVS26YD7IALWMA",
      awsSecretKey = "DAIJ3Jk57m7wWkKLj+IGqhryoYx3+JN7NZoJoVXj",
      awsSessionToken =
        "FQoDYXdzEND//////////wEaDEPIC8JXf96C8rkAxSKsAdyBBJAr0ZWX4g/DzJwMlXYLXjuiZZq2kzILauD" +
          "JssHtOd8BhV3K98GJzLRPyGZoW/dBNpRRSC+jIw4PBtTV51DNYGHPBqeP3LoXqo8Jq4tKE0eemFcZ1+mXauSSNIDEJJJ++cFH+6" +
          "vG0AjfjotAMFpOhs8pu5y9VKm+WkesndrDLIpPpVtXvSgtYZV+kAE8tFAutxWjUSEYxgnocaxlrr5jborKFSJfxnkmqcso8PfJ0AU=",
      videoPath = "MSTAR_Data/Train_Data/2S1/video.mp4",
      targetPrefix = s"baile/data/albums/${UUID.randomUUID.toString}",
      frameCaptureRate = 10
    )

    val imageFiles = Seq(
      File(
        filePath = "frame1.png",
        fileSize = 20,
        fileName = "frame1.png"
      ),
      File(
        filePath = "frame2.png",
        fileSize = 30,
        fileName = "frame2.png"
      ),
      File(
        filePath = "frame3.png",
        fileSize = 40,
        fileName = "frame3.png"
      )
    )

    val videoFile = File(
      filePath = "job-master/videos/dc-c213ghq-video.mp4",
      fileSize = 581923L,
      fileName = "video.mp4"
    )

    val result = S3VideoImportResult(
      imageFiles = imageFiles,
      videoFile = Some(videoFile),
      videoFrameRate = 60,
      videoHeight = 500,
      videoWidth = 1200
    )

  }

}
