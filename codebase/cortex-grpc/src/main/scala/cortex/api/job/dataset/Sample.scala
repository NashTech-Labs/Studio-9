package cortex.api.job.dataset

import java.util.UUID

import cortex.api.job.common.{ FailedFile, File }

object Sample {

  val s3DatasetImportRequest = S3DatasetImportRequest(
    bucketName = "dev.deepcortex.ai",
    awsRegion = "us-east-1",
    awsAccessKey = Some("ASIAJPAVS26YD7IALWMA"),
    awsSecretKey = Some("DAIJ3Jk57m7wWkKLj+IGqhryoYx3+JN7NZoJoVXj"),
    awsSessionToken = Some(
      "FQoDYXdzEND//////////wEaDEPIC8JXf96C8rkAxSKsAdyBBJAr0ZWX4g/DzJwMlXYLXjuiZZq2kzILauD" +
      "JssHtOd8BhV3K98GJzLRPyGZoW/dBNpRRSC+jIw4PBtTV51DNYGHPBqeP3LoXqo8Jq4tKE0eemFcZ1+mXauSSNIDEJJJ++cFH+6" +
      "vG0AjfjotAMFpOhs8pu5y9VKm+WkesndrDLIpPpVtXvSgtYZV+kAE8tFAutxWjUSEYxgnocaxlrr5jborKFSJfxnkmqcso8PfJ0AU="
    ),
    datasetPath = "MSTAR_Data/Train_Data/2S1/",
    targetPrefix = s"baile/data/albums/${UUID.randomUUID.toString}"
  )

  val s3DatasetExportRequest = S3DatasetExportRequest(
    bucketName = "dev.deepcortex.ai",
    awsRegion = "us-east-1",
    awsAccessKey = Some("ASIAJPAVS26YD7IALWMA"),
    awsSecretKey = Some("DAIJ3Jk57m7wWkKLj+IGqhryoYx3+JN7NZoJoVXj"),
    awsSessionToken = Some(
      "FQoDYXdzEND//////////wEaDEPIC8JXf96C8rkAxSKsAdyBBJAr0ZWX4g/DzJwMlXYLXjuiZZq2kzILauD" +
      "JssHtOd8BhV3K98GJzLRPyGZoW/dBNpRRSC+jIw4PBtTV51DNYGHPBqeP3LoXqo8Jq4tKE0eemFcZ1+mXauSSNIDEJJJ++cFH+6" +
      "vG0AjfjotAMFpOhs8pu5y9VKm+WkesndrDLIpPpVtXvSgtYZV+kAE8tFAutxWjUSEYxgnocaxlrr5jborKFSJfxnkmqcso8PfJ0AU="
    ),
    datasetPath = "MSTAR_Data/Train_Data/2S1/",
    targetPrefix = s"baile/data/albums/${UUID.randomUUID.toString}"
  )

  val failedFiles = Seq(
    FailedFile(
      filePath = "HB19528.000",
      errorMessage = Some("Bad SAR header")
    )
  )

  val file = File(
    filePath = "job-master/videos/dc-c213ghq-video.mp4",
    fileSize = 581923L,
    fileName = "video.mp4"
  )

  val uploadedDatasetFile = Seq(
    UploadedDatasetFile(
      file = Some(
        File(
          filePath = "HB19528.000",
          fileSize = 20,
          fileName = "HB19528.000.png"
        )
      ),
      metadata = Map(
        "location"             -> "-10.023978989477996,33.437548033986744,6.861708891866691",
        "azimuthAngle"         -> "-15.553092151830981",
        "scaleFactor"          -> "20.166520149611195",
        "originalImageChipUrl" -> "foo",
        "chipUniqueIdentifier" -> "chip0",
        "dateTime"             -> "2017-11-13T16:05:27.800Z",
        "layoverAngle"         -> "-36.83274257891712",
        "grazingAngle"         -> "79.55534001547893",
        "sampleSpacing"        -> "11.373227032872343"
      )
    ),
    UploadedDatasetFile(
      file = Some(
        File(
          filePath = "HB19526.000",
          fileSize = 30,
          fileName = "HB19526.000.png"
        )
      ),
      metadata = Map(
        "location"             -> "-10.023978989477996,33.437548033986744,6.861708891866691",
        "azimuthAngle"         -> "-15.553092151830981",
        "scaleFactor"          -> "20.166520149611195",
        "originalImageChipUrl" -> "foo",
        "chipUniqueIdentifier" -> "chip0",
        "label"                -> "s3://example/chip0",
        "dateTime"             -> "2017-11-13T16:05:27.800Z",
        "layoverAngle"         -> "-36.83274257891712",
        "grazingAngle"         -> "79.55534001547893",
        "sampleSpacing"        -> "11.373227032872343"
      )
    ),
    UploadedDatasetFile(
      file = Some(
        File(
          filePath = "subdir/HB19522.000",
          fileSize = 40,
          fileName = "subdir/HB19522.000.png"
        )
      ),
      metadata = Map()
    )
  )

  val s3DatasetImportResponse = S3DatasetImportResponse(
    datasets = uploadedDatasetFile,
    failedFiles = failedFiles
  )

  val s3DatasetExportResponse = S3DatasetExportResponse(
    datasets = uploadedDatasetFile,
    failedFiles = failedFiles
  )

}
