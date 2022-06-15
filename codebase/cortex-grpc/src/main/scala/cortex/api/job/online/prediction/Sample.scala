package cortex.api.job.online.prediction

import java.util.UUID

import cortex.api.job.common.FailedFile

// scalastyle:off
object Sample {

  val images = Seq(
    Image(key = "MSTAR_Data/Train_Data/2S1/HB19526.000", size =  235943),
    Image(key = "MSTAR_Data/Train_Data/2S1/HB19527.000", size =  745334),
    Image(key = "MSTAR_Data/Train_Data/2S1/HB19528.000", size =  534544),
    Image(key = "MSTAR_Data/Train_Data/2S1/HB19529.000", size =  654657),
    Image(key = "MSTAR_Data/Train_Data/2S1/HB19530.000", size =  867671),
    Image(key = "MSTAR_Data/Train_Data/2S1/HB19531.000", size =  654643),
    Image(key = "MSTAR_Data/Train_Data/2S1/HB19532.000", size =  126947),
    Image(key = "MSTAR_Data/Train_Data/2S1/HB19533.000", size =  895700),
    Image(key = "MSTAR_Data/Train_Data/2S1/HB19534.000", size =  995789)
  )

  val predictRequest = PredictRequest(
    bucketName = "dev.deepcortex.ai",
    awsRegion = "us-east-1",
    awsAccessKey = "ASIAJPAVS26YD7IALWMA",
    awsSecretKey = "DAIJ3Jk57m7wWkKLj+IGqhryoYx3+JN7NZoJoVXj",
    awsSessionToken =
        "FQoDYXdzEND//////////wEaDEPIC8JXf96C8rkAxSKsAdyBBJAr0ZWX4g/DzJwMlXYLXjuiZZq2kzILauD" +
            "JssHtOd8BhV3K98GJzLRPyGZoW/dBNpRRSC+jIw4PBtTV51DNYGHPBqeP3LoXqo8Jq4tKE0eemFcZ1+mXauSSNIDEJJJ++cFH+6" +
            "vG0AjfjotAMFpOhs8pu5y9VKm+WkesndrDLIpPpVtXvSgtYZV+kAE8tFAutxWjUSEYxgnocaxlrr5jborKFSJfxnkmqcso8PfJ0AU=",
    images = images,
    targetPrefix = s"baile/data/albums/${UUID.randomUUID.toString}",
    modelId = UUID.randomUUID().toString
  )

  val classifiedImages = Seq(
    LabledImage(
      filePath = "HB19528.000",
      fileSize = 20,
      fileName = "HB19528.000.png",
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
      ),
      label = "btr1",
      confidence = 0.8
    ),
    LabledImage(
      filePath = "HB19526.000",
      fileSize = 30,
      fileName = "HB19526.000.png",
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
      ),
      label = "btr2",
      confidence = 0.4
    )
  )

  val failedFiles = Seq(
    FailedFile(
      filePath = "HB19528.000",
      errorMessage = Some("Bad SAR header")
    )
  )

  val predictResponse = PredictResponse(
    images = classifiedImages,
    failedFiles = failedFiles,
    s3ResultsCsvPath = "s3://some.bucket/some/path/to/root/folder"
  )
}
