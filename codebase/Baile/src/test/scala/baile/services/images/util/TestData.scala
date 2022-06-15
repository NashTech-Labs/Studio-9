package baile.services.images.util

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.images.AlbumStatus.Saving
import baile.domain.images.{ AlbumStorageAccessParameters, _ }
import baile.domain.images.augmentation.{ RotationParams, _ }
import baile.domain.remotestorage.S3TemporaryCredentials
import baile.services.usermanagement.util.TestData.SampleUser
import play.api.libs.json._

object TestData {

  val DateTime: Instant = Instant.now()
  val AlbumEntity = Album(
    ownerId = SampleUser.id,
    name = "name",
    status = Saving,
    `type` = AlbumType.Source,
    labelMode = AlbumLabelMode.Classification,
    created = DateTime,
    updated = DateTime,
    inLibrary = false,
    picturesPrefix = "picturesPrefix",
    description = None,
    augmentationTimeSpentSummary = None
  )
  val OutputAlbumEntity: Album = AlbumEntity.copy(
    name = "AugmentedAlbum",
    inLibrary = true
  )
  val PictureEntity = Picture(
    albumId = "albumId",
    filePath = "filePath",
    fileName = "fileName",
    fileSize = None,
    caption = None,
    predictedCaption = None,
    tags = Seq.empty,
    predictedTags = Seq.empty,
    meta = Map.empty,
    originalPictureId = None,
    appliedAugmentations = None
  )
  val AlbumEntityWithId = WithId(AlbumEntity, "albumId")
  val OutputAlbumEntityWithId = WithId(OutputAlbumEntity, "albumId")
  val PictureEntityWithId = WithId(PictureEntity, "pictureId")
  val AlbumResponseData: JsObject = Json.obj(
    "id" -> JsString("albumId"),
    "ownerId" -> JsString(SampleUser.id.toString),
    "name" -> JsString("name"),
    "created" -> JsString(DateTime.toString),
    "updated" -> JsString(DateTime.toString),
    "status" -> JsString("SAVING"),
    "inLibrary" -> JsBoolean(false),
    "type" -> JsString("SOURCE"),
    "locked" -> false,
    "labelMode" -> JsString("CLASSIFICATION")
  )
  val PictureResponseData: JsObject = Json.obj(
    "id" -> JsString("pictureId"),
    "albumId" -> JsString("albumId"),
    "filepath" -> JsString("filePath"),
    "filename" -> JsString("fileName"),
    "tags" -> JsArray(),
    "predictedTags" -> JsArray()
  )
  val AppliedAugmentationBlurring = AppliedBlurringParams(1f)
  val AugmentedPictureEntityWithId = WithId(
    PictureEntity.copy(
      appliedAugmentations = Some(Seq(AppliedAugmentation(
        generalParams = AppliedAugmentationBlurring,
        extraParams = Map("foo" -> 0.2f),
        internalParams = Map("bar" -> 30f)
      )))
    ),
    "pictureId"
  )
  val AugmentedPictureResponseData: JsObject = Json.obj(
    "id" -> JsString("pictureId"),
    "albumId" -> JsString("albumId"),
    "filepath" -> JsString("filePath"),
    "filename" -> JsString("fileName"),
    "tags" -> JsArray(),
    "predictedTags" -> JsArray(),
    "augmentationsApplied" -> Seq(Json.obj(
      "sigma" -> JsNumber(1),
      "augmentationType" -> JsString("BLURRING"),
      "extraParams" -> Json.obj("foo" -> JsNumber(0.2))
    ))
  )
  val AugmentedAlbumResponse: JsObject = Json.obj(
    "id" -> JsString("albumId"),
    "ownerId" -> JsString(SampleUser.id.toString),
    "name" -> JsString("AugmentedAlbum"),
    "created" -> JsString(DateTime.toString),
    "updated" -> JsString(DateTime.toString),
    "status" -> JsString("SAVING"),
    "inLibrary" -> JsBoolean(true),
    "type" -> JsString("SOURCE"),
    "locked" -> false,
    "labelMode" -> JsString("CLASSIFICATION")
  )
  val TempCredentials = S3TemporaryCredentials(
    region = "us-east-1",
    bucketName = "S3_BUCKET",
    accessKey = "1234E435",
    secretKey = "1234E435",
    sessionToken = "12345"
  )
  val AlbumStorageAccessParametersEntity = AlbumStorageAccessParameters(
    TempCredentials,
    "/album/1234"
  )
  val AlbumStorageAccessParametersResponseData: JsObject = Json.obj(
    "region" -> JsString(TempCredentials.region),
    "albumBasePath" -> JsString(AlbumStorageAccessParametersEntity.albumBasePath),
    "bucketName" -> JsString(TempCredentials.bucketName),
    "accessKey" -> JsString(TempCredentials.accessKey),
    "secretKey" -> JsString(TempCredentials.secretKey),
    "sessionToken" -> JsString(TempCredentials.sessionToken)
  )
  val AugmentedPicturesListResponse: JsObject = Json.obj(
    "data" -> Seq(AugmentedPictureResponseData),
    "count" -> 1
  )

  val AlbumUpdateRequestAsJson: String =
    """{
      |  "name":"name",
      |  "labelMode":"CLASSIFICATION"
      |}""".stripMargin

  val AlbumCreateRequestAsJson: String =
    """{
      |  "name":"name",
      |  "labelMode":"CLASSIFICATION"
      |}""".stripMargin

  val PictureCreateOrUpdateAsJson = """{"caption":"caption","tags":[]}"""

  val AddPicturesRequestWithKeepExistingTrueAsJson = """{"pictureMetaList" : [], "keepExisting": true}"""
  val AddPicturesRequestWithKeepExistingFalseAsJson = """{"pictureMetaList" : [], "keepExisting": false}"""

  val AugmentationsParamTestData = Seq(RotationParams(Seq(30, 60), true, 1))

  val InvalidAugmentationsParamTestData = Seq(RotationParams(Seq(30, 1960), true, 1))

  val DefaultRotationParams = RotationParams(angles = Seq(45, 90, 135, 180, 270), resize = true, 1)

  val DefaultShearingParams = ShearingParams(angles = Seq(15, 30), resize = true, 1)


  val DefaultNoisingParams = NoisingParams(
    noiseSignalRatios = Seq(0.15f, 0.3f, 0.45f, 0.6f, 0.75f),
    1
  )

  val DefaultZoomInParams = ZoomInParams(
    magnifications = Seq(1.2f, 1.5f, 1.75f, 2.0f, 2.5f),
    resize = true,
    1
  )

  val DefaultZoomOutParams = ZoomOutParams(
    shrinkFactors = Seq(0.2f, 0.5f, 0.33f, 0.5f, 0.7f),
    resize = true,
    1
  )

  val DefaultOcclusionParams = OcclusionParams(
    occAreaFractions = Seq(0.05f, 0.1f, 0.25f, 0.5f, 0.65f),
    mode = OcclusionMode.Zero,
    isSarAlbum = false,
    tarWinSize = 32,
    1
  )

  val DefaultTranslationParams = TranslationParams(
    translateFractions = Seq(0.1f, 0.2f, 0.3f, 0.4f),
    mode = TranslationMode.Constant,
    resize = false,
    4
  )

  val DefaultMirroringParams = MirroringParams(
    axesToFlip = Seq(MirroringAxisToFlip.Horizontal, MirroringAxisToFlip.Vertical, MirroringAxisToFlip.Both),
    1
  )

  val DefaultBlurringParams = BlurringParams(
    sigmaList = Seq(0.5f, 1.0f, 2.0f, 4.0f),
    1
  )

  val DefaultSaltPepperParams = SaltPepperParams(
    knockoutFractions = Seq(0.05f, 0.1f, 0.2f, 0.3f),
    pepperProbability = 0.5f,
    1
  )

  val DefaultPhotometricDistortParams = PhotometricDistortParams(
    PhotometricDistortAlphaBounds(
      min = 0.5f,
      max = 1.5f
    ),
    deltaMax = 18,
    1
  )

  val DefaultCroppingParams = CroppingParams(
    cropAreaFractions = Seq(0.25f, 0.36f, 0.49f, 0.64f),
    cropsPerImage = 1,
    resize = false,
    1
  )

  val AugmentationRequestsData = Seq(
    DefaultRotationParams,
    DefaultShearingParams,
    DefaultCroppingParams,
    DefaultBlurringParams,
    DefaultNoisingParams,
    DefaultZoomInParams,
    DefaultZoomOutParams,
    DefaultOcclusionParams,
    DefaultSaltPepperParams,
    DefaultMirroringParams,
    DefaultTranslationParams,
    DefaultPhotometricDistortParams
  )

  val AugmentationRequestsResult = Seq(DefaultRotationParams, DefaultShearingParams)

  val DefaultValueResponse: JsArray = Json.arr(
    Json.obj(
      "angles" -> Seq(JsNumber(45), JsNumber(90), JsNumber(135), JsNumber(180), JsNumber(270)),
      "resize" -> JsBoolean(true),
      "bloatFactor" -> JsNumber(1),
      "augmentationType" -> JsString("ROTATION")
    ),
    Json.obj(
      "angles" -> Seq(JsNumber(15), JsNumber(30)),
      "resize" -> JsBoolean(true),
      "bloatFactor" -> JsNumber(1),
      "augmentationType" -> JsString("SHEARING")
    ),
  )
}
