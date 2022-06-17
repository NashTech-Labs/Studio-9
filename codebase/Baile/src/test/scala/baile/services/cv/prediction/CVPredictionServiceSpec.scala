package baile.services.cv.prediction

import java.time.Instant
import java.util.UUID

import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.cv.prediction.CVPredictionDao
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.common.{ CortexModelReference, Version }
import baile.domain.cv.model.{ CVModelType, _ }
import baile.domain.cv.prediction.CVPrediction
import baile.domain.dcproject.DCProjectPackage
import baile.domain.cv.model.tlprimitives.{ CVTLModelPrimitive, CVTLModelPrimitiveType }
import baile.domain.images._
import baile.domain.process.{ Process, ProcessStatus, ResultHandlerMeta }
import baile.domain.table.{ Table, TableStatisticsStatus, TableStatus, TableType }
import baile.domain.usermanagement.User
import baile.services.asset.sharing.AssetSharingService
import baile.services.cortex.job.CortexJobService
import baile.services.cortex.job.SupportedCortexJobTypes.SupportedCortexJobType
import baile.domain.cv.LabelOfInterest
import baile.services.cv.CVTLModelPrimitiveService
import baile.services.cv.model.{ CVModelCommonService, CVModelService }
import baile.services.cv.prediction.CVPredictionService.CVPredictionCreateError._
import baile.domain.cv.prediction.CVModelPredictOptions
import baile.services.dcproject.DCProjectPackageService
import baile.services.images.{ AlbumService, ImagesCommonService }
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.services.remotestorage.RemoteStorageService
import baile.services.table.TableService
import baile.services.{ images, usermanagement }
import cats.implicits._
import cortex.api.job.common.ClassReference
import cortex.api.job.computervision._
import play.api.libs.json.JsObject


class CVPredictionServiceSpec extends ExtendedBaseSpec {

  trait Setup {

    val albumService: AlbumService = mock[AlbumService]
    val dao: CVPredictionDao = mock[CVPredictionDao]
    val cortexJobService: CortexJobService = mock[CortexJobService]
    val processService: ProcessService = mock[ProcessService]
    val cvModelService: CVModelService = mock[CVModelService]
    val cvModelCommonService: CVModelCommonService = mock[CVModelCommonService]
    val tableService: TableService = mock[TableService]
    val cvTlModelPrimitiveService: CVTLModelPrimitiveService = mock[CVTLModelPrimitiveService]
    val imagesCommonService: ImagesCommonService = mock[ImagesCommonService]
    val pictureStorage: RemoteStorageService = mock[RemoteStorageService]
    val assetSharingService: AssetSharingService = mock[AssetSharingService]
    val projectService: ProjectService = mock[ProjectService]
    val packageService: DCProjectPackageService = mock[DCProjectPackageService]

    implicit val user: User = usermanagement.util.TestData.SampleUser

    val randomUUID: UUID = UUID.randomUUID()

    val picture = WithId(Picture(
      albumId = randomUUID.toString,
      filePath = "filePath",
      fileName = "fileName",
      fileSize = Some(20l),
      caption = None,
      predictedCaption = None,
      tags = Seq.empty,
      predictedTags = Seq.empty,
      meta = Map.empty,
      originalPictureId = None,
      appliedAugmentations = None
    ), "id")

    val cvPredictionService = new CVPredictionService(
      albumService,
      cvModelService,
      cvModelCommonService,
      tableService,
      cvTlModelPrimitiveService,
      imagesCommonService,
      pictureStorage,
      packageService,
      dao,
      cortexJobService,
      processService,
      assetSharingService,
      projectService
    )(ec, logger)

    val dateTime = Instant.now
    val album = WithId(Album(
      randomUUID,
      "name",
      AlbumStatus.Saving,
      AlbumType.Source,
      AlbumLabelMode.Classification,
      dateTime,
      dateTime,
      inLibrary = true,
      "prefix",
      None,
      None,
      None
    ), randomString())
    val packageId = randomString()
    val dcProjectPackageSample = WithId(DCProjectPackage(
      name = "packageName",
      created = Instant.now(),
      ownerId = Some(UUID.randomUUID),
      location = Some("/package/"),
      version = Some(Version(1, 0, 0, None)),
      dcProjectId = Some("projectId"),
      description = Some("package description"),
      isPublished = true
    ), packageId)
    val architecture = WithId(
      CVTLModelPrimitive(
        packageId = packageId,
        name = "SCAE",
        isNeural = true,
        moduleName = "ml_lib.feature_extractors.backbones",
        className = "SCAE",
        cvTLModelPrimitiveType = CVTLModelPrimitiveType.UTLP,
        params = Seq(),
        description = None
      ),
      randomString()
    )
    val classifier = WithId(
      CVTLModelPrimitive(
        packageId = packageId,
        name = "FCN_1",
        isNeural = true,
        moduleName = "ml_lib.classifiers.backbones",
        className = "FCN_1",
        cvTLModelPrimitiveType = CVTLModelPrimitiveType.Classifier,
        params = Seq(),
        description = None
      ),
      randomString()
    )


  }

  "CVPredictionServiceSpec#Predict" should {

    val labelOfInterest = LabelOfInterest(
      label = randomString(),
      threshold = randomInt(999)
    )

    val cvPredictionOptions = CVModelPredictOptions(
      loi = Some(Seq(labelOfInterest)),
      defaultVisualThreshold = Some(randomInt(999)),
      iouThreshold = Some(randomInt(999))
    )

    "predict without any errors/exception" in new Setup {
      val modelType = CVModelType.TL(CVModelType.TLConsumer.Classifier(classifier.id), architecture.id)
      dao.count(*)(*) shouldReturn future(0)
      cvModelCommonService.createOutputAlbum(
        *,
        *,
        *,
        *,
        *,
        *,
        *,
        *
      ) shouldReturn future(WithId(album.entity, randomString()))
      cvModelCommonService.createPredictionTable(*)(*) shouldReturn
        future(WithId(
          Table(
            ownerId = user.id,
            name = "name",
            repositoryId = "repositoryId",
            databaseId = "databaseId",
            created = Instant.now(),
            updated = Instant.now(),
            status = TableStatus.Active,
            columns = Seq(),
            `type` = TableType.Derived,
            inLibrary = true,
            tableStatisticsStatus = TableStatisticsStatus.Pending,
            description = None
          ),
          randomString()
        ))
      cvModelCommonService.buildCortexTLConsumer(*, *) shouldCall realMethod
      cvModelCommonService.buildCortexTLModel(*, *, *) shouldCall realMethod
      cvModelService.get(*)(*) shouldReturn
        future(
          WithId(CVModel(
            ownerId = randomUUID,
            name = "name",
            created = dateTime,
            updated = dateTime,
            status = CVModelStatus.Active,
            description = None,
            inLibrary = true,
            cortexFeatureExtractorReference = None,
            cortexModelReference = Some(CortexModelReference(
              "cid",
              "cfilepath"
            )),
            `type` = modelType,
            classNames = Some(Seq("foo")),
            featureExtractorId = Some("feId"),
            experimentId = None
          ), "id").asRight
        )
      future(
        WithId(album.entity, "album-id").asRight
      ) willBe returned by albumService.get("input-album")(*)
      future(Seq(images.util.TestData.PictureEntityWithId)) willBe returned by
        imagesCommonService.getPictures(*[String], false)
      imagesCommonService.getImagesPathPrefix(*)
        .shouldReturn("path")
      dao.create(*[CVPrediction])(*) shouldReturn {
        future("42669c4f-668a-4dca-b312-f46acd71d53f")
      }
      future(classifier) willBe returned by cvTlModelPrimitiveService.loadTLConsumerPrimitive(modelType.consumer)
      future(architecture) willBe returned by
        cvTlModelPrimitiveService.loadFeatureExtractorArchitecturePrimitive(architecture.id)
      packageService.loadPackageMandatory(packageId) shouldReturn future(dcProjectPackageSample)
      ClassReference(
        moduleName = classifier.entity.moduleName,
        className = classifier.entity.className,
        packageLocation = dcProjectPackageSample.entity.location
      ) willBe returned by cvModelCommonService.buildClassReference(
        classifier.entity,
        dcProjectPackageSample.entity
      )
      ClassReference(
        moduleName = architecture.entity.moduleName,
        className = architecture.entity.className,
        packageLocation = dcProjectPackageSample.entity.location
      ) willBe returned by cvModelCommonService.buildClassReference(
        architecture.entity,
        dcProjectPackageSample.entity
      )
      cortexJobService.submitJob(
        *[PredictRequest],
        *
      )(eqTo(implicitly[SupportedCortexJobType[PredictRequest]])) shouldReturn {
        future(
          UUID.fromString("42669c4f-668a-4dca-b312-f46acd71d53f")
        )
      }
      tableService.buildTableMeta(*) shouldCall realMethod
      processService.startProcess(
        UUID.fromString("42669c4f-668a-4dca-b312-f46acd71d53f"),
        "42669c4f-668a-4dca-b312-f46acd71d53f",
        AssetType.CvPrediction,
        classOf[CVPredictionResultHandler],
        CVPredictionResultHandler.Meta("42669c4f-668a-4dca-b312-f46acd71d53f", false, user.id),
        user.id
      ) shouldReturn {
        future(
          WithId(
            Process(
              targetId = "target-id",
              targetType = AssetType.CvModel,
              ownerId = UUID.fromString("42669c4f-668a-4dca-b312-f46acd71d53f"),
              authToken = None,
              jobId = UUID.fromString("42669c4f-668a-4dca-b312-f46acd71d53f"),
              status = ProcessStatus.Running,
              progress = None,
              estimatedTimeRemaining = None,
              created = dateTime,
              started = None,
              completed = None,
              errorCauseMessage = None,
              errorDetails = None,
              onComplete = ResultHandlerMeta(
                "class-name",
                JsObject.empty
              ),
              auxiliaryOnComplete = Seq.empty
            ),
            "uu-id"
          )
        )
      }
      whenReady(cvPredictionService.create(
        "model-id",
        "input-album",
        Some("predict-name"),
        Some("description"),
        Some("output-albim-name"),
        Some(cvPredictionOptions),
        false
      )) { result =>
        assert(result.isRight)
        assert(result.right.get.entity.inputAlbumId == "input-album")
        assert(result.right.get.entity.name == "predict-name")
        assert(result.right.get.entity.description == Some("description"))
      }
    }

    "evaluate without any errors/exception" in new Setup {
      dao.count(*)(*) shouldReturn future(0)
      future(Seq(picture)) willBe returned by imagesCommonService.getPictures(*[String], true)
      cvModelCommonService.createOutputAlbum(
        *,
        *,
        *,
        *,
        *,
        *,
        *,
        *
      ) shouldReturn future(WithId(album.entity, randomString()))
      cvModelCommonService.createPredictionTable(*)(*) shouldReturn
        future(WithId(
          Table(
            ownerId = user.id,
            name = "name",
            repositoryId = "repositoryId",
            databaseId = "databaseId",
            created = Instant.now(),
            updated = Instant.now(),
            status = TableStatus.Active,
            columns = Seq(),
            `type` = TableType.Derived,
            inLibrary = true,
            tableStatisticsStatus = TableStatisticsStatus.Pending,
            description = None
          ),
          randomString()
        ))
      imagesCommonService.convertPicturesToCortexTaggedImages(*) shouldReturn Seq()
      cvModelService.get(*)(*) shouldReturn
        future(
          WithId(CVModel(
            ownerId = randomUUID,
            name = "name",
            created = dateTime,
            updated = dateTime,
            status = CVModelStatus.Active,
            description = None,
            cortexFeatureExtractorReference = None,
            cortexModelReference = Some(CortexModelReference(
              "cid",
              "cfilepath"
            )),
            `type` = CVModelType.TL(CVModelType.TLConsumer.Classifier(classifier.id), architecture.id),
            classNames = Some(Seq("foo")),
            featureExtractorId = Some("feId"),
            inLibrary = true,
            experimentId = None
          ), "id").asRight
        )
      cvTlModelPrimitiveService.validateAlbumAndModelCompatibility(
        *,
        *,
        *
      ) shouldReturn ().asRight
      future(
        WithId(album.entity, "album-id").asRight
      ) willBe returned by albumService.get("input-album")(*)
      imagesCommonService.getImagesPathPrefix(*)
        .shouldReturn("path")
      dao.create(*[CVPrediction])(*) shouldReturn {
        future("42669c4f-668a-4dca-b312-f46acd71d53f")
      }
      future(classifier) willBe returned by
        cvTlModelPrimitiveService.loadTLConsumerPrimitive(CVModelType.TLConsumer.Classifier(classifier.id))
      future(architecture) willBe returned by
        cvTlModelPrimitiveService.loadFeatureExtractorArchitecturePrimitive(architecture.id)
      packageService.loadPackageMandatory(packageId) shouldReturn future(dcProjectPackageSample)
      ClassReference(
        moduleName = classifier.entity.moduleName,
        className = classifier.entity.className,
        packageLocation = dcProjectPackageSample.entity.location
      ) willBe returned by cvModelCommonService.buildClassReference(
        classifier.entity,
        dcProjectPackageSample.entity
      )
      ClassReference(
        moduleName = architecture.entity.moduleName,
        className = architecture.entity.className,
        packageLocation = dcProjectPackageSample.entity.location
      ) willBe returned by cvModelCommonService.buildClassReference(
        architecture.entity,
        dcProjectPackageSample.entity
      )
      cvModelCommonService.buildCortexTLConsumer(*, *) shouldCall realMethod
      cvModelCommonService.buildCortexTLModel(*, *, *) shouldCall realMethod
      tableService.buildTableMeta(*) shouldCall realMethod
      cortexJobService.submitJob(
        *[EvaluateRequest],
        *
      )(implicitly[SupportedCortexJobType[EvaluateRequest]]) shouldReturn {
        future(
          UUID.fromString("42669c4f-668a-4dca-b312-f46acd71d53f")
        )
      }
      processService.startProcess(
        UUID.fromString("42669c4f-668a-4dca-b312-f46acd71d53f"),
        "42669c4f-668a-4dca-b312-f46acd71d53f",
        AssetType.CvPrediction,
        classOf[CVPredictionResultHandler],
        CVPredictionResultHandler.Meta("42669c4f-668a-4dca-b312-f46acd71d53f", true, user.id),
        user.id
      ) shouldReturn {
        future(
          WithId(
            Process(
              targetId = "target-id",
              targetType = AssetType.CvModel,
              ownerId = UUID.fromString("42669c4f-668a-4dca-b312-f46acd71d53f"),
              authToken = None,
              jobId = UUID.fromString("42669c4f-668a-4dca-b312-f46acd71d53f"),
              status = ProcessStatus.Running,
              progress = None,
              estimatedTimeRemaining = None,
              created = dateTime,
              started = None,
              completed = None,
              errorCauseMessage = None,
              errorDetails = None,
              onComplete = ResultHandlerMeta(
                "class-name",
                JsObject.empty
              ),
              auxiliaryOnComplete = Seq.empty
            ),
            "uu-id"
          )
        )
      }
      whenReady(cvPredictionService.create(
        "model-id",
        "input-album",
        Some("predict-name"),
        Some("description"),
        Some("output-albim-name"),
        Some(cvPredictionOptions),
        true
      )) { result =>
        assert(result.isRight)
        assert(result.right.get.entity.inputAlbumId == "input-album")
        assert(result.right.get.entity.name == "predict-name")
      }
    }

    "return error when album label mode not compatible during evaluation" in new Setup {
      val modelType = CVModelType.TL(CVModelType.TLConsumer.Classifier(classifier.id), architecture.id)
      dao.count(*)(*) shouldReturn future(0)
      cvModelService.get(*)(*) shouldReturn
        future(
          WithId(CVModel(
            ownerId = randomUUID,
            name = "name",
            created = dateTime,
            updated = dateTime,
            status = CVModelStatus.Active,
            description = None,
            cortexFeatureExtractorReference = None,
            cortexModelReference = Some(CortexModelReference(
              "cid",
              "cfilepath"
            )),
            `type` = modelType,
            classNames = Some(Seq("foo")),
            featureExtractorId = Some("feId"),
            inLibrary = true,
            experimentId = None
          ), "id").asRight
        )
      future(
        WithId(album.entity.copy(labelMode = AlbumLabelMode.Localization), "album-id").asRight
      ) willBe returned by albumService.get("localization-album")(*)
      cvTlModelPrimitiveService.validateAlbumAndModelCompatibility(
        *,
        *,
        *
      ) shouldCall realMethod
      whenReady(cvPredictionService.create(
        "model-id",
        "localization-album",
        Some("predict-result"),
        Some("description"),
        Some("output-albim-name"),
        Some(cvPredictionOptions),
        true
      )) { result =>
        assert(result.isLeft)
        assert(result.left.get === UnsupportedAlbumLabelMode)
      }
    }

    "return error when name is empty" in new Setup {
      whenReady(cvPredictionService.create(
        "model-id",
        "input-album",
        Some(""),
        Some("description"),
        Some("output-albim-name"),
        Some(cvPredictionOptions),
        false
      )) { result =>
        assert(result.isLeft)
        assert(result.left.get === PredictionNameCanNotBeEmpty)
      }
    }

    "throw an exception when prediction already exists" in new Setup {
      dao.count(*)(*) shouldReturn future(1)
      whenReady(cvPredictionService.create(
        "model-id",
        "input-album",
        Some("predict-result"),
        Some("description"),
        Some("output-albim-name"),
        Some(cvPredictionOptions),
        false
      )) { result =>
        assert(result.isLeft)
        assert(result.left.get === PredictionAlreadyExists)
      }
    }

  }

}
