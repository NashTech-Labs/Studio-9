package baile.services.cv.model

import java.time.Instant
import java.util.UUID

import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.asset.Filters.{ InLibraryIs, NameIs, OwnerIdIs }
import baile.dao.cv.model.CVModelDao
import baile.dao.cv.model.tlprimitives.CVTLModelPrimitiveDao
import baile.daocommons.WithId
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.common.{ ClassReference, CortexModelReference, Version }
import baile.domain.cv.model._
import baile.domain.cv.model.tlprimitives.{ CVTLModelPrimitive, CVTLModelPrimitiveType }
import baile.domain.dcproject.DCProjectPackage
import baile.domain.usermanagement.User
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.MLEntityExportImportService
import baile.services.common.MLEntityExportImportService.{ EntityFileSavedResult, EntityImportError }
import baile.services.cortex.job.CortexJobService
import baile.services.cv.CVTLModelPrimitiveService
import baile.services.cv.model.CVModelRandomGenerator._
import baile.services.cv.model.CVModelService._
import baile.services.cv.model.util.export.CVModelExportMeta
import baile.services.dcproject.DCProjectPackageService
import baile.services.images.{ AlbumService, ImagesCommonService }
import baile.services.process.ProcessService
import baile.services.process.util.ProcessRandomGenerator._
import baile.services.project.ProjectService
import baile.services.remotestorage.RemoteStorageService
import baile.services.usermanagement.util.TestData.SampleUser
import cats.data.EitherT
import cats.implicits._
import play.api.libs.json.OWrites

import scala.concurrent.Future

class CVModelServiceSpec extends ExtendedBaseSpec {

  trait Setup {

    val dao: CVModelDao = mock[CVModelDao]
    val cvTLModelPrimitiveDao = mock[CVTLModelPrimitiveDao]
    val cvModelCommonService: CVModelCommonService = mock[CVModelCommonService]
    val albumService: AlbumService = mock[AlbumService]
    val imagesCommonService: ImagesCommonService = mock[ImagesCommonService]
    val cortexJobService: CortexJobService = mock[CortexJobService]
    val processService: ProcessService = mock[ProcessService]
    val assetSharingService: AssetSharingService = mock[AssetSharingService]
    val exportImportService: MLEntityExportImportService = mock[MLEntityExportImportService]
    val projectService: ProjectService = mock[ProjectService]
    val cvModelPrimitiveService: CVTLModelPrimitiveService = mock[CVTLModelPrimitiveService]
    val packageService: DCProjectPackageService = mock[DCProjectPackageService]
    val mlEntitiesStorage: RemoteStorageService = mock[RemoteStorageService]

    val service = new CVModelService(
      dao,
      cvTLModelPrimitiveDao,
      cortexJobService,
      cvModelCommonService,
      imagesCommonService,
      processService,
      assetSharingService,
      exportImportService,
      projectService,
      cvModelPrimitiveService,
      packageService,
      mlEntitiesStorage
    )

    implicit val user: User = SampleUser

  }

  "CVModelService#export" should {

    "return source of model meta and model file" in new Setup {
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
      val modelType = CVModelType.TL(CVModelType.TLConsumer.Classifier(classifier.id), architecture.id)
      val model = randomModel(
        status = CVModelStatus.Active,
        ownerId = user.id,
        cortexModelReference = Some(CortexModelReference(randomString(), randomString())),
        modelType = modelType
      )
      val exportedModelMeta = ByteString(randomString())
      val exportedModelFileContent = ByteString(randomString())

      dao.get(model.id) shouldReturn future(Some(model))
      future(classifier) willBe returned by cvModelPrimitiveService.loadTLConsumerPrimitive(modelType.consumer)
      future(architecture) willBe returned by cvModelPrimitiveService.loadFeatureExtractorArchitecturePrimitive(architecture.id)
      packageService.loadPackageMandatory(packageId) shouldReturn future(dcProjectPackageSample)
      exportImportService.exportEntity(
        eqTo(model.entity.cortexModelReference.get.cortexFilePath),
        any[CVModelExportMeta]
      )(eqTo(implicitly[OWrites[CVModelExportMeta]])) shouldReturn future(Source(List(
        exportedModelMeta,
        ByteString('\u0000'),
        exportedModelFileContent
      )))

      whenReady {
        for {
          source <- service.export(model.id).map(_.right.get)
          modelFileContent <- source.runReduce(_ ++ _)
        } yield modelFileContent
      } { result =>
        val (modelMeta, preModelFile) = result.span(_ != '\u0000')
        modelMeta shouldBe exportedModelMeta
        val modelFile = preModelFile.drop(1)
        modelFile shouldBe exportedModelFileContent
      }
    }

    "return error when model is not active" in new Setup {
      val model = randomModel(status = CVModelStatus.Pending, ownerId = user.id)
      dao.get(model.id) shouldReturn future(Some(model))
      whenReady(service.export(model.id)) { result =>
        result shouldBe CVModelServiceError.ModelNotActive.asLeft
      }
    }

    "return error when active model did not contain file path" in new Setup {
      val model = randomModel(
        status = CVModelStatus.Active,
        ownerId = user.id,
        cortexModelReference = None
      )
      dao.get(model.id) shouldReturn future(Some(model))

      whenReady(service.export(model.id)) { result =>
        result shouldBe CVModelServiceError.CantExportCVModel.asLeft
      }
    }

  }

  "CVModelService#importModel" should {

    val packageId = randomString()
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
      "architecture-id"
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
      "classifier-id"
    )

    "successfully import tl model" in new Setup {
      val exportedModel = randomModel(
        ownerId = user.id,
        status = CVModelStatus.Active,
        modelType = CVModelType.TL(CVModelType.TLConsumer.Classifier(classifier.id), architecture.id),
        featureExtractorId = None
      ).entity
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
      val architectureClassReference = CVModelExportMeta.ClassReference(
        moduleName = architecture.entity.moduleName,
        className = architecture.entity.className,
        packageName = dcProjectPackageSample.entity.name,
        packageVersion = Some(CVModelExportMeta.Version(1, 0, 0, None))
      )
      val modelType = CVModelExportMeta.CVModelType.TL(
        consumer = CVModelExportMeta.CVModelType.TLConsumer.Classifier(
          classReference = CVModelExportMeta.ClassReference(
            moduleName = classifier.entity.moduleName,
            className = classifier.entity.className,
            packageName = dcProjectPackageSample.entity.name,
            packageVersion = Some(CVModelExportMeta.Version(1, 0, 0, None))
          )
        ),
        featureExtractorReference = architectureClassReference
      )
      val exportedModelMeta = CVModelExportMeta(
        exportedModel,
        modelType
      )
      val exportedModelFileSource = Source(List(ByteString(randomString(100))))

      val newName = "newname"
      val importedModel = exportedModel.copy(
        name = newName,
        inLibrary = true,
        status = CVModelStatus.Saving,
        cortexModelReference = None,
        cortexFeatureExtractorReference = None
      )

      val importParams = future(Map("name" -> newName))

      val cortexJobId = UUID.randomUUID()

      exportImportService.importEntity[CVModelImportError, WithId[CVModel], CVModelExportMeta](
        exportedModelFileSource,
        *,
        *
      )(*, eqTo(user), *) shouldAnswer { (
        _: Source[_, _],
        metaValidator: CVModelExportMeta => EitherT[Future, CVModelImportError, Unit],
        handler: EntityFileSavedResult[CVModelExportMeta] => Future[Either[CVModelImportError, WithId[CVModel]]]
      ) =>
        val result = for {
          _ <- metaValidator(exportedModelMeta)
          result <- EitherT(handler(EntityFileSavedResult(exportedModelMeta, "filePath")))
        } yield result
        result.leftMap(EntityImportError.ImportHandlingFailed(_)).value
      }
      dao.count(OwnerIdIs(user.id) && NameIs(newName) && InLibraryIs(true)) shouldReturn future(0)
      cvTLModelPrimitiveDao.listAll(*, *) shouldReturn future(Seq(classifier)) andThen future(Seq(architecture))
      packageService.getPackageByNameAndVersion(
        dcProjectPackageSample.entity.name,
        dcProjectPackageSample.entity.version
      ) shouldReturn future(Some(dcProjectPackageSample))
      dao.create(*[CVModel])(*) shouldReturn future(randomString())
      cvModelCommonService.buildCortexTLConsumer(*, *) shouldCall realMethod
      cvModelCommonService.buildCortexTLModel(*, *, *) shouldCall realMethod
      cortexJobService.submitJob(
        *,
        user.id
      )(*) shouldReturn future(cortexJobId)
      processService.startProcess(
        cortexJobId,
        *,
        AssetType.CvModel,
        classOf[CVModelImportResultHandler],
        *[CVModelImportResultHandler.Meta],
        user.id
      )(*) shouldReturn future(randomProcess())

      whenReady(service.importModel(exportedModelFileSource, importParams)) { result =>
        result.map(_.entity.copy(
          created = importedModel.created,
          updated = importedModel.updated
        )) shouldBe importedModel.asRight
      }

    }

    "successfully import custom model" in new Setup {
      val modelClassReference = ClassReference(packageId, "module1", "class1")
      val exportedModel = randomModel(
        ownerId = user.id,
        status = CVModelStatus.Active,
        modelType = CVModelType.Custom(
          classReference = modelClassReference,
          labelMode = None
        ),
        featureExtractorId = None
      ).entity
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
      val modelType = CVModelExportMeta.CVModelType.Custom(
        classReference = CVModelExportMeta.ClassReference(
          moduleName = modelClassReference.moduleName,
          className = modelClassReference.className,
          packageName = dcProjectPackageSample.entity.name,
          packageVersion = Some(CVModelExportMeta.Version(1, 0, 0, None))
        ),
        labelMode = None
      )
      val exportedModelMeta = CVModelExportMeta(
        exportedModel,
        modelType
      )
      val exportedModelFileSource = Source(List(ByteString(randomString(100))))

      val newName = "newname"
      val importedModel = exportedModel.copy(
        name = newName,
        inLibrary = true,
        status = CVModelStatus.Saving,
        cortexModelReference = None,
        cortexFeatureExtractorReference = None
      )

      val importParams = future(Map("name" -> newName))

      val cortexJobId = UUID.randomUUID()

      exportImportService.importEntity[CVModelImportError, WithId[CVModel], CVModelExportMeta](
        exportedModelFileSource,
        *,
        *
      )(*, eqTo(user), *) shouldAnswer { (
        _: Source[_, _],
        metaValidator: CVModelExportMeta => EitherT[Future, CVModelImportError, Unit],
        handler: EntityFileSavedResult[CVModelExportMeta] => Future[Either[CVModelImportError, WithId[CVModel]]]
      ) =>
        val result = for {
          _ <- metaValidator(exportedModelMeta)
          result <- EitherT(handler(EntityFileSavedResult(exportedModelMeta, "filePath")))
        } yield result
        result.leftMap(EntityImportError.ImportHandlingFailed(_)).value
      }
      dao.count(OwnerIdIs(user.id) && NameIs(newName) && InLibraryIs(true)) shouldReturn future(0)
      packageService.getPackageByNameAndVersion(
        dcProjectPackageSample.entity.name,
        dcProjectPackageSample.entity.version
      ) shouldReturn future(Some(dcProjectPackageSample))
      cvModelCommonService.buildCortexCustomModel(*) shouldCall realMethod
      dao.create(*[CVModel])(*) shouldReturn future(randomString())
      cortexJobService.submitJob(
        *,
        user.id
      )(*) shouldReturn future(cortexJobId)
      processService.startProcess(
        cortexJobId,
        *,
        AssetType.CvModel,
        classOf[CVModelImportResultHandler],
        *[CVModelImportResultHandler.Meta],
        user.id
      )(*) shouldReturn future(randomProcess())

      whenReady(service.importModel(exportedModelFileSource, importParams)) { result =>
        result.map(_.entity.copy(
          created = importedModel.created,
          updated = importedModel.updated
        )) shouldBe importedModel.asRight
      }
    }

    "return error if package is not published and also not owned by user" in new Setup {
      val modelClassReference = ClassReference(packageId, "module1", "class1")
      val exportedModel = randomModel(
        ownerId = user.id,
        status = CVModelStatus.Active,
        modelType = CVModelType.Custom(
          classReference = modelClassReference,
          labelMode = None
        ),
        featureExtractorId = None
      ).entity
      val dcProjectPackageSample = WithId(DCProjectPackage(
        name = "packageName",
        created = Instant.now(),
        ownerId = Some(UUID.randomUUID),
        location = Some("/package/"),
        version = Some(Version(1, 0, 0, None)),
        dcProjectId = Some("projectId"),
        description = Some("package description"),
        isPublished = false
      ), packageId)
      val modelType = CVModelExportMeta.CVModelType.Custom(
        classReference = CVModelExportMeta.ClassReference(
          moduleName = modelClassReference.moduleName,
          className = modelClassReference.className,
          packageName = dcProjectPackageSample.entity.name,
          packageVersion = Some(CVModelExportMeta.Version(1, 0, 0, None))
        ),
        labelMode = None
      )
      val exportedModelMeta = CVModelExportMeta(
        exportedModel,
        modelType
      )
      val exportedModelFileSource = Source(List(ByteString(randomString(100))))
      val newName = "newname"
      val importParams = future(Map("name" -> newName))
      exportImportService.importEntity[CVModelImportError, WithId[CVModel], CVModelExportMeta](
        exportedModelFileSource,
        *,
        *
      )(*, eqTo(user), *) shouldAnswer { (
        _: Source[_, _],
        metaValidator: CVModelExportMeta => EitherT[Future, CVModelImportError, Unit],
        handler: EntityFileSavedResult[CVModelExportMeta] => Future[Either[CVModelImportError, WithId[CVModel]]]
      ) =>
        val result = for {
          _ <- metaValidator(exportedModelMeta)
          result <- EitherT(handler(EntityFileSavedResult(exportedModelMeta, "filePath")))
        } yield result
        result.leftMap(EntityImportError.ImportHandlingFailed(_)).value
      }
      dao.count(OwnerIdIs(user.id) && NameIs(newName) && InLibraryIs(true)) shouldReturn future(0)
      packageService.getPackageByNameAndVersion(
        dcProjectPackageSample.entity.name,
        dcProjectPackageSample.entity.version
      ) shouldReturn future(None)

      whenReady(service.importModel(exportedModelFileSource, importParams)) { result =>
        result shouldBe CVModelImportError.PackageNotFound(
          dcProjectPackageSample.entity.name,
          dcProjectPackageSample.entity.version
        ).asLeft
      }
    }

  }

  "CVModelService#update" should {

    trait UpdateSetup extends Setup {
      val model = randomModel(ownerId = user.id)
      dao.get(model.id) shouldReturn future(Some(model))
    }

    "update model name and description" in new UpdateSetup {
      dao.update(model.id, *)(*) shouldAnswer { (_: String, updater: CVModel => CVModel) =>
        future(Some(
          WithId(updater(model.entity), model.id)
        ))
      }
      dao.count(*)(*) shouldReturn future(0)

      val newName = "newname"
      val newDescription = "newdescription"
      whenReady(service.update(
        id = model.id,
        newName = Some(newName),
        newDescription = Some(newDescription)
      )) { result =>
        val updatedModel = result.right.get
        updatedModel.id shouldBe model.id
        updatedModel.entity.name shouldBe newName
        updatedModel.entity.description.get shouldBe newDescription
      }
    }

    "return empty model name error" in new UpdateSetup {
      whenReady(service.update(
        id = model.id,
        newName = Some(""),
        newDescription = None
    ))(_ shouldBe CVModelServiceError.EmptyModelName.asLeft)
    }

  }

  "CVModelService#delete" should {

    "delete model" in new Setup {
      val model = randomModel(
        ownerId = user.id
      )

      dao.get(model.id) shouldReturn future(Some(model))
      projectService.removeAssetFromAllProjects(AssetReference(model.id, AssetType.CvModel)) shouldReturn future(())
      processService.cancelProcesses(model.id, AssetType.CvModel) shouldReturn future(().asRight)
      assetSharingService.deleteSharesForAsset(model.id, AssetType.CvModel) shouldReturn future(())
      dao.delete(model.id) shouldReturn future(true)

      whenReady(service.delete(model.id)) { result =>
        result shouldBe ().asRight
        dao.delete(model.id) wasCalled once
      }
    }

  }

  "CVModelService#getStateFileUrl" should {

    "get state file URL" in new Setup {
      val modelFilePath = randomString()
      val model = randomModel(
        status = CVModelStatus.Active,
        ownerId = user.id,
        cortexModelReference = Some(CortexModelReference(randomString(), modelFilePath))
      )
      val expectedUrl = randomString()

      dao.get(model.id) shouldReturn future(Some(model))
      mlEntitiesStorage.getExternalUrl(modelFilePath) shouldReturn expectedUrl

      whenReady(service.getStateFileUrl(model.id)) { url =>
        url shouldBe Right(expectedUrl)
      }
    }

    "return error if model with given id not found" in new Setup {
      val modelId = randomString()
      dao.get(modelId) shouldReturn future(None)

      whenReady(service.getStateFileUrl(modelId)) { result =>
        result shouldBe CVModelServiceError.ModelNotFound.asLeft
      }
    }

    "return error when model is not active" in new Setup {
      val model = randomModel(
        status = CVModelStatus.Pending,
        ownerId = user.id
      )
      dao.get(model.id) shouldReturn future(Some(model))

      whenReady(service.getStateFileUrl(model.id)) { result =>
        result shouldBe CVModelServiceError.ModelNotActive.asLeft
      }
    }

    "return error when model did not contain file path" in new Setup {
      val model = randomModel(
        status = CVModelStatus.Active,
        ownerId = user.id,
        cortexModelReference = None
      )
      dao.get(model.id) shouldReturn future(Some(model))

      whenReady(service.getStateFileUrl(model.id)) { result =>
        result shouldBe CVModelServiceError.ModelFilePathNotFound.asLeft
      }
    }

  }

  "CVModelService#save" should {

    trait SaveSetup extends Setup {
      val model = randomModel(ownerId = user.id, status = CVModelStatus.Active)
      dao.get(model.id) shouldReturn future(Some(model))
    }

    "should save the model successfully" in new SaveSetup {
      val modelName = "name"
      val modelDescription = Some("description")
      val expectedModel = model.copy(entity = model.entity.copy(
        name = modelName,
        description = modelDescription,
        inLibrary = true
      ))

      dao.update(model.id, *)(*) shouldAnswer { (_: String, updater: CVModel => CVModel) =>
        future(Some(
          WithId(updater(model.entity), model.id)
        ))
      }
      dao.count(*)(*) shouldReturn future(0)

      whenReady(service.save(model.id, modelName, modelDescription)) { result =>
        result shouldBe Right(expectedModel)
      }
    }

    "return error response when model name already exists" in new SaveSetup {
      dao.count(*)(*) shouldReturn future(1)

      whenReady(service.save(model.id, "name", None)) { result =>
        result shouldBe Left(CVModelServiceError.NameIsTaken)
      }
    }

    "return error response when model name is empty" in new SaveSetup {
      whenReady(service.save(model.id, "", None)) { result =>
        result shouldBe Left(CVModelServiceError.EmptyModelName)
      }
    }

    "return error response when model is not active" in new Setup {
      val model = randomModel(
        ownerId = user.id,
        status = CVModelStatus.Training
      )
      dao.get(model.id) shouldReturn future(Some(model))

      whenReady(service.save(model.id, "name", None)) { result =>
        result shouldBe Left(CVModelServiceError.ModelNotActive)
      }
    }

    "return error response when model already is saved to library" in new Setup {
      val model = randomModel(
        ownerId = user.id,
        status = CVModelStatus.Active,
        inLibrary = true
      )
      dao.get(model.id) shouldReturn future(Some(model))

      whenReady(service.save(model.id, "name", None)) { result =>
        result shouldBe Left(CVModelServiceError.ModelAlreadyInLibrary)
      }
    }
  }

}
