package baile.routes.common

import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import baile.daocommons.WithId
import baile.daocommons.filters.Filter
import baile.domain.asset.AssetScope
import baile.domain.common.S3Bucket
import baile.domain.dcproject.DCProjectPackage
import baile.domain.cv.model.tlprimitives.CVTLModelPrimitiveType
import baile.domain.pipeline.category.Category
import baile.domain.usermanagement.User
import baile.routes.RoutesSpec
import baile.services.common.{ AuthenticationService, S3BucketService }
import baile.services.cv.CVTLModelPrimitiveService
import baile.services.cv.CVTLModelPrimitiveService.ExtendedCVTLModelPrimitive
import baile.services.cv.model.CVModelService
import baile.services.cv.prediction.CVPredictionService
import baile.services.dataset.DatasetService
import baile.services.dcproject.DCProjectService
import baile.services.experiment.ExperimentService
import baile.services.images.{ AlbumService, DefaultAugmentationValuesService }
import baile.services.pipeline.PipelineService
import baile.services.pipeline.category.CategoryService
import baile.services.project.ProjectService
import baile.services.table.TableService
import baile.services.tabular.model.TabularModelService
import baile.services.tabular.prediction.TabularPredictionService
import baile.services.usermanagement.util.TestData.SampleUser
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.libs.json._

class CommonRoutesSpec extends RoutesSpec {

  private val authenticationService: AuthenticationService = mock[AuthenticationService]
  private val s3BucketService: S3BucketService = mock[S3BucketService]
  private val cvModelService: CVModelService = mock[CVModelService]
  private val tableService = mock[TableService]
  private val tabularModelService = mock[TabularModelService]
  private val albumService = mock[AlbumService]
  private val projectService = mock[ProjectService]
  private val augmentationValuesService = mock[DefaultAugmentationValuesService]
  private val cvModelPrimitiveService = mock[CVTLModelPrimitiveService]
  private val operatorCategoryService = mock[CategoryService]
  private val experimentService = mock[ExperimentService]
  private val pipelineService = mock[PipelineService]
  private val datasetService = mock[DatasetService]
  private val dcProjectService = mock[DCProjectService]
  private val cvPredictionService = mock[CVPredictionService]
  private val tabularPredictionService = mock[TabularPredictionService]
  private val packageId = randomString()
  val dcProjectPackageSample = WithId(DCProjectPackage(
    name = "packageName",
    created = Instant.now(),
    ownerId = Some(UUID.randomUUID),
    location = Some("/package/"),
    version = None,
    dcProjectId = Some("projectId"),
    description = Some("package description"),
    isPublished = true
  ), packageId)
  private val cvArchitectures = List(
    ExtendedCVTLModelPrimitive(
      id = randomString(),
      name = "SCAE",
      isNeural = true,
      moduleName = "ml_lib.feature_extractors.backbones",
      className = "SCAE",
      packageName = dcProjectPackageSample.entity.name,
      packageVersion = dcProjectPackageSample.entity.version,
      params = Seq()
    ),
    ExtendedCVTLModelPrimitive(
      id = randomString(),
      name = "VGG16",
      isNeural = true,
      moduleName = "ml_lib.feature_extractors.backbones",
      className = "Vgg16",
      packageName = dcProjectPackageSample.entity.name,
      packageVersion = dcProjectPackageSample.entity.version,
      params = Seq()
    )
  )
  private val cvClassifiers = List(
    ExtendedCVTLModelPrimitive(
      id = randomString(),
      name = "FCN_1",
      isNeural = true,
      moduleName = "ml_lib.classifiers.backbones",
      className = "FCN_1",
      packageName = dcProjectPackageSample.entity.name,
      packageVersion = dcProjectPackageSample.entity.version,
      params = Seq()
    ),
    ExtendedCVTLModelPrimitive(
      id = randomString(),
      name = "KPCA_MNL",
      isNeural = true,
      moduleName = "ml_lib.classifiers.backbones",
      className = "KPCA_MNL",
      packageName = dcProjectPackageSample.entity.name,
      packageVersion = dcProjectPackageSample.entity.version,
      params = Seq()
    )
  )
  private val cvLocalizers = List(
    ExtendedCVTLModelPrimitive(
      id = randomString(),
      name = "RFBNet",
      isNeural = true,
      moduleName = "ml_lib.detectors.backbones",
      className = "RFBNet",
      packageName = dcProjectPackageSample.entity.name,
      packageVersion = dcProjectPackageSample.entity.version,
      params = Seq()
    )
  )
  private val decoders = List(
    ExtendedCVTLModelPrimitive(
      id = randomString(),
      name = "STACKED",
      isNeural = true,
      moduleName = "ml_lib.classifiers.backbones",
      className = "STACKED",
      packageName = dcProjectPackageSample.entity.name,
      packageVersion = dcProjectPackageSample.entity.version,
      params = Seq()
    )
  )
  private val categories = Seq(Category("id", "name", "icon"))

  when(authenticationService.authenticate(userToken)).thenReturn(future(Some(SampleUser)))
  when(s3BucketService.listAll()).thenReturn(future(Seq(WithId(
    S3Bucket.AccessOptions(
      region = randomOf("us-east-1", "us-east-2"),
      bucketName = randomString(),
      accessKey = Some(randomString(20)),
      secretKey = Some(randomString(32)),
      sessionToken = Some(randomString(32))
    ),
    randomString()
  ))))
  when(cvModelService.count(
    any[Option[AssetScope]], any[Option[String]], any[Option[String]], any[Option[String]]
  )(any[User])).thenReturn(future(Right(100)))
  when(tableService.count(
    any[Option[AssetScope]], any[Option[String]], any[Option[String]], any[Option[String]]
  )(any[User])).thenReturn(future(Right(100)))
  when(tabularModelService.count(
    any[Option[AssetScope]], any[Option[String]], any[Option[String]], any[Option[String]]
  )(any[User])).thenReturn(future(Right(100)))
  when(albumService.count(
    any[Option[AssetScope]], any[Option[String]], any[Option[String]], any[Option[String]]
  )(any[User])).thenReturn(future(Right(100)))
  when(projectService.count(any[Filter])(any[User])).thenReturn(future(Right(100)))
  when(experimentService.count(
    any[Option[AssetScope]], any[Option[String]], any[Option[String]], any[Option[String]]
  )(any[User])).thenReturn(future(Right(100)))
  when(pipelineService.count(
    any[Option[AssetScope]], any[Option[String]], any[Option[String]], any[Option[String]]
  )(any[User])).thenReturn(future(Right(100)))
  when(datasetService.count(
    any[Option[AssetScope]], any[Option[String]], any[Option[String]], any[Option[String]]
  )(any[User])).thenReturn(future(Right(100)))
  when(dcProjectService.count(
    any[Option[AssetScope]], any[Option[String]], any[Option[String]], any[Option[String]]
  )(any[User])).thenReturn(future(Right(100)))
  when(cvPredictionService.count(
    any[Option[AssetScope]], any[Option[String]], any[Option[String]], any[Option[String]]
  )(any[User])).thenReturn(future(Right(100)))
  when(tabularPredictionService.count(
    any[Option[AssetScope]], any[Option[String]], any[Option[String]], any[Option[String]]
  )(any[User])).thenReturn(future(Right(100)))

  when(cvModelPrimitiveService.getCVTLModelPrimitivesWithPackageInfo(CVTLModelPrimitiveType.UTLP)(SampleUser))
    .thenReturn(future(cvArchitectures))
  when(cvModelPrimitiveService.getCVTLModelPrimitivesWithPackageInfo(CVTLModelPrimitiveType.Classifier)(SampleUser))
    .thenReturn(future(cvClassifiers))
  when(cvModelPrimitiveService.getCVTLModelPrimitivesWithPackageInfo(CVTLModelPrimitiveType.Detector)(SampleUser))
    .thenReturn(future(cvLocalizers))
  when(cvModelPrimitiveService.getCVTLModelPrimitivesWithPackageInfo(CVTLModelPrimitiveType.Decoder)(SampleUser))
    .thenReturn(future(decoders))

  override val routes: Route = new CommonRoutes(
    conf,
    authenticationService,
    s3BucketService,
    cvModelService,
    tableService,
    tabularModelService,
    albumService,
    projectService,
    augmentationValuesService,
    cvModelPrimitiveService,
    operatorCategoryService,
    datasetService,
    experimentService,
    pipelineService,
    dcProjectService,
    cvPredictionService,
    tabularPredictionService
  ).routes

  "GET /s3buckets" should {
    "return buckets list" in {
      Get("/s3buckets").signed.check {
        status shouldBe StatusCodes.OK
      }
    }

    "require authentication" in {
      Get("/s3buckets").check {
        status shouldBe StatusCodes.Unauthorized
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /me/stats" should {
    "return stats data" in {
      Get("/me/stats?scope=all").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject].keys should contain allOf(
          "tablesCount",
          "flowsCount",
          "modelsCount",
          "projectsCount",
          "cvModelsCount",
          "albumsCount",
          "binaryDatasetsCount",
          "pipelinesCount",
          "experimentsCount",
          "cvPredictionsCount",
          "tabularPredictionsCount",
          "dcProjectsCount"
        )
      }
    }

    "require authentication" in {
      Get("/me/stats").check {
        status shouldBe StatusCodes.Unauthorized
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /config/cv-architectures" should {
    "return list of available architecture types" in {
      Get("/config/cv-architectures").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsValue] shouldBe JsArray(
          cvArchitectures.map { architecture  =>
            JsObject(Map(
              "id" -> JsString(architecture.id),
              "name" -> JsString(architecture.name),
              "moduleName" -> JsString(architecture.moduleName),
              "className" -> JsString(architecture.className),
              "packageName" -> JsString(architecture.packageName)
            ) ++ Seq(
              architecture.packageVersion.map(x => "packageVersion" -> JsString(x.toString))
            ).flatten.toMap)
          }
        )
      }
    }
  }

  "GET /config/cv-classifiers" should {
    "return list of available classifiers" in {
      Get("/config/cv-classifiers").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsValue] shouldBe JsArray(
          cvClassifiers.map { classifier =>
            JsObject(Map(
              "id" -> JsString(classifier.id),
              "name" -> JsString(classifier.name),
              "moduleName" -> JsString(classifier.moduleName),
              "className" -> JsString(classifier.className),
              "packageName" -> JsString(classifier.packageName),
              "isNeural" -> JsBoolean(classifier.isNeural)
            ) ++ Seq(
              classifier.packageVersion.map(x => "packageVersion" -> JsString(x.toString))
            ).flatten.toMap)
          }
        )
      }
    }
  }

  "GET /operator-categories" should {
    when(operatorCategoryService.listAll) thenReturn future(categories)
    "return list of categories" in {
      Get("/config/operator-categories").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsValue] shouldBe JsArray(
          categories.map { operatorCategory =>
            JsObject(Map(
              "id" -> JsString(operatorCategory.id),
              "name" -> JsString(operatorCategory.name),
              "icon" -> JsString(operatorCategory.icon)
            ))
          }
        )
      }
    }
  }

  "GET /config/cv-detectors" should {
    "return list of available detectors" in {
      Get("/config/cv-detectors").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsValue] shouldBe JsArray(
          cvLocalizers map { detector  =>
            JsObject(Map(
              "id" -> JsString(detector.id),
              "name" -> JsString(detector.name),
              "moduleName" -> JsString(detector.moduleName),
              "className" -> JsString(detector.className),
              "packageName" -> JsString(detector.packageName),
              "isNeural" -> JsBoolean(detector.isNeural)
            ) ++ Seq(
              detector.packageVersion.map(x => "packageVersion" -> JsString(x.toString))
            ).flatten.toMap)
          }
        )
      }
    }
  }

  "GET /config/cv-decoders" should {
    "return list of available encoders" in {
      Get("/config/cv-decoders").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsValue] shouldBe JsArray(
          decoders.map { encoder  =>
            JsObject(Map(
              "id" -> JsString(encoder.id),
              "name" -> JsString(encoder.name),
              "moduleName" -> JsString(encoder.moduleName),
              "className" -> JsString(encoder.className),
              "packageName" -> JsString(encoder.packageName),
              "isNeural" -> JsBoolean(encoder.isNeural)
            ) ++ Seq(
              encoder.packageVersion.map(x => "packageVersion" -> JsString(x.toString))
            ).flatten.toMap)
          }
        )
      }
    }
  }

}
