package baile.services.cv

import java.time.Instant
import java.util.UUID

import baile.BaseSpec
import baile.dao.cv.model.tlprimitives.CVTLModelPrimitiveDao
import baile.daocommons.WithId
import baile.daocommons.filters.TrueFilter
import baile.daocommons.sorting.SortBy
import baile.domain.common.{ ClassReference, Version }
import baile.domain.cv.model.CVModelType
import baile.domain.cv.model.tlprimitives.{ CVTLModelPrimitive, CVTLModelPrimitiveType }
import baile.domain.dcproject.DCProjectPackage
import baile.domain.images._
import baile.services.cv.CVTLModelPrimitiveService.{ CVTLModelPrimitiveServiceError, ExtendedCVTLModelPrimitive }
import baile.services.cv.model.CVModelTrainPipelineHandler.CVModelCreateError
import baile.services.dcproject.DCProjectPackageService
import baile.services.dcproject.DCProjectPackageService.DCProjectPackageServiceError
import baile.services.usermanagement.util.TestData
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.when
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.concurrent.ExecutionContext

class CVTLModelPrimitiveServiceSpec extends BaseSpec with TableDrivenPropertyChecks {

  val cvTLModelPrimitiveDao = mock[CVTLModelPrimitiveDao]
  val packageService = mock[DCProjectPackageService]
  val cvModelPrimitiveService = new CVTLModelPrimitiveService(
    cvTLModelPrimitiveDao,
    packageService
  )
  val architecture: String = randomString()
  val unKnownArchitecture: String = randomString()
  val packageId = randomString()
  val dcProjectPackageLocation = "/package/"
  val dcProjectPackageSample = WithId(DCProjectPackage(
    name = "packageName",
    created = Instant.now(),
    ownerId = Some(UUID.randomUUID),
    location = Some(dcProjectPackageLocation),
    version = Some(Version(1, 0, 0, None)),
    dcProjectId = Some("projectId"),
    description = Some("package description"),
    isPublished = true
  ), packageId)
  val cvFeatureExtractorArchitecture = WithId(
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
  val cvFeatureExtractorArchitectureExtendedResponse = ExtendedCVTLModelPrimitive(
    id = cvFeatureExtractorArchitecture.id,
    name = cvFeatureExtractorArchitecture.entity.name,
    moduleName = cvFeatureExtractorArchitecture.entity.moduleName,
    className = cvFeatureExtractorArchitecture.entity.className,
    packageName = dcProjectPackageSample.entity.name,
    packageVersion = dcProjectPackageSample.entity.version,
    isNeural = cvFeatureExtractorArchitecture.entity.isNeural,
    params = Seq()
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
  val localizer = WithId(
    CVTLModelPrimitive(
      packageId = packageId,
      name = "RFBNet",
      isNeural = true,
      moduleName = "ml_lib.detectors.backbones",
      className = "RFBNet",
      cvTLModelPrimitiveType = CVTLModelPrimitiveType.Detector,
      params = Seq(),
      description = None
    ),
    randomString()
  )
  val cvModelTypeClassifier = CVModelType.TL(CVModelType.TLConsumer.Classifier(classifier.entity.name), "SCAE")
  val cvModelTypeLocalizer = CVModelType.TL(CVModelType.TLConsumer.Localizer(localizer.entity.name), "SCAE")
  val invalidCVModelTypeClassifier = CVModelType.TL(CVModelType.TLConsumer.Classifier(randomString()), "SCAE")
  val invalidCVModelTypeLocalizer = CVModelType.TL(CVModelType.TLConsumer.Localizer(randomString()), "SCAE")
  val user = TestData.SampleUser
  val video = Video(
    filePath = "filePath",
    fileSize = 20l,
    fileName = "fileName",
    frameRate = 60,
    frameCaptureRate = 1,
    height = 860,
    width = 680
  )
  val classificationAlbum = Album(
    ownerId = user.id,
    name = "album",
    status = AlbumStatus.Saving,
    `type` = AlbumType.Derived,
    labelMode = AlbumLabelMode.Classification,
    created = Instant.now(),
    updated = Instant.now(),
    inLibrary = false,
    picturesPrefix = "prefix",
    video = Some(video),
    description = None,
    augmentationTimeSpentSummary = None
  )
  val localizationAlbum = classificationAlbum.copy(labelMode = AlbumLabelMode.Localization)

  "CVTLModelPrimitiveService#getAllCVFeatureExtractorArchitectures" should {
    "be able to get all cv Feature extractor architectures" in {
      when(packageService.listAll(TrueFilter, Nil)(SampleUser)) thenReturn future(Seq(dcProjectPackageSample).asRight)
      when(cvTLModelPrimitiveDao.listAll(
        filterContains(
          CVTLModelPrimitiveDao.CVTLModelPrimitiveTypeIs(CVTLModelPrimitiveType.UTLP) &&
            CVTLModelPrimitiveDao.PackageIdIn(Seq(packageId))
        ),
        any[Option[SortBy]]
      )(any[ExecutionContext])) thenReturn
        future(Seq(cvFeatureExtractorArchitecture))
      whenReady(
        cvModelPrimitiveService.getCVTLModelPrimitivesWithPackageInfo(CVTLModelPrimitiveType.UTLP)(SampleUser)
      )(_ shouldBe Seq(cvFeatureExtractorArchitectureExtendedResponse))
    }

  }

  "CVTLModelPrimitiveService#validateAlbumAndModelCompatibility" should {
    "return unit if modelType is compatible with albumLabelMode" in {
      forAll(Table(
        ("modelType", "labelMode"),
        (cvModelTypeClassifier, AlbumLabelMode.Classification),
        (cvModelTypeLocalizer, AlbumLabelMode.Localization),
        (
          CVModelType.Custom(
            ClassReference("packageId", "moduleName", "className"),
            Some(AlbumLabelMode.Localization)
          ),
          AlbumLabelMode.Localization
        )
      )) { (modelType, labelMode) =>

        cvModelPrimitiveService.validateAlbumAndModelCompatibility(
          labelMode,
          modelType,
          CVModelCreateError.ModelTypeIsIncompatibleWithAlbum
        ) shouldBe Right(())
      }
    }

    "return error if modelType is not compatible with albumLabelMode" in {
      forAll(Table(
        ("modelType", "labelMode"),
        (cvModelTypeClassifier, AlbumLabelMode.Localization),
        (cvModelTypeLocalizer, AlbumLabelMode.Classification),
        (
          CVModelType.Custom(
            ClassReference("packageId", "moduleName", "className"),
            Some(AlbumLabelMode.Classification)
          ),
          AlbumLabelMode.Localization
        )
      )) { (modelType, labelMode) =>

        cvModelPrimitiveService.validateAlbumAndModelCompatibility(
          labelMode,
          modelType,
          CVModelCreateError.ModelTypeIsIncompatibleWithAlbum
        ) shouldBe CVModelCreateError.ModelTypeIsIncompatibleWithAlbum.asLeft
      }
    }

  }

  "CVTLModelPrimitiveService#validateAlbumAndTLConsumerCompatibility" should {
    "return unit if TL consumer is compatible with albumLabelMode" in {
      cvModelPrimitiveService.validateAlbumAndTLConsumerCompatibility(
        AlbumLabelMode.Localization,
        cvModelTypeLocalizer.consumer,
        CVModelCreateError.ModelTypeIsIncompatibleWithAlbum
      ) shouldBe Right(())
    }

    "return error if TL consumer is not compatible with albumLabelMode" in {
      cvModelPrimitiveService.validateAlbumAndTLConsumerCompatibility(
        AlbumLabelMode.Classification,
        cvModelTypeLocalizer.consumer,
        CVModelCreateError.ModelTypeIsIncompatibleWithAlbum
      ) shouldBe CVModelCreateError.ModelTypeIsIncompatibleWithAlbum.asLeft
    }

  }

  "CVTLModelPrimitiveService#validFeatureExtractorArchitecture" should {
    "return unit when the architecture is valid" in {
      cvModelPrimitiveService.validateFEArchitectureCVTLModelPrimitiveType(
        cvFeatureExtractorArchitecture.entity.cvTLModelPrimitiveType,
        CVModelCreateError.InvalidArchitecture
      ) shouldBe ().asRight
    }

    "return error when the architecture is invalid" in {
      cvModelPrimitiveService.validateFEArchitectureCVTLModelPrimitiveType(
        classifier.entity.cvTLModelPrimitiveType,
        CVModelCreateError.InvalidArchitecture
      ) shouldBe CVModelCreateError.InvalidArchitecture.asLeft
    }

  }

  "CVTLModelPrimitiveService#validateModelTypeAndOperatorType" should {
    "return unit if TL consumer matches operator type" in {
      forAll(Table(
        ("consumer", "operatorType"),
        (cvModelTypeClassifier.consumer, CVTLModelPrimitiveType.Classifier),
        (cvModelTypeLocalizer.consumer, CVTLModelPrimitiveType.Detector),
      )) { (consumer, cvTLModelPrimitiveType) =>

        cvModelPrimitiveService.validateModelTypeAndCVTLModelPrimitiveType(
          consumer,
          cvTLModelPrimitiveType,
          CVModelCreateError.InvalidCVModelType
        ) shouldBe ().asRight
      }
    }

    "return error if TL consumer doesn't match operator type" in {
      forAll(Table(
        ("consumer", "cvTLModelPrimitiveType"),
        (cvModelTypeClassifier.consumer, CVTLModelPrimitiveType.UTLP),
        (cvModelTypeLocalizer.consumer, CVTLModelPrimitiveType.Decoder),
      )) { (consumer, cvTLModelPrimitiveType) =>

        cvModelPrimitiveService.validateModelTypeAndCVTLModelPrimitiveType(
          consumer,
          cvTLModelPrimitiveType,
          CVModelCreateError.InvalidCVModelType
        ) shouldBe CVModelCreateError.InvalidCVModelType.asLeft
      }
    }

  }

  "CVTLModelPrimitiveService#getModelPrimitiveWithPackage" should {
    "return model primitive with package" in {
      when(cvTLModelPrimitiveDao.get(eqTo(classifier.id))(any[ExecutionContext])).thenReturn(future(Some(classifier)))
      when(packageService.get(packageId)(user)).thenReturn(future(dcProjectPackageSample.asRight))

      whenReady(
        cvModelPrimitiveService.getModelPrimitiveWithPackage(classifier.id)(user)
      )(
        _ shouldBe (classifier, dcProjectPackageSample).asRight
      )
    }

    "return NotFound error if primitive does not exist" in {
      when(cvTLModelPrimitiveDao.get(eqTo(classifier.id))(any[ExecutionContext])).thenReturn(future(None))

      whenReady(
        cvModelPrimitiveService.getModelPrimitiveWithPackage(classifier.id)(user)
      )(
        _ shouldBe CVTLModelPrimitiveServiceError.NotFound(classifier.id).asLeft
      )
    }

    "return AccessDenied error if user does not have access to the package" in {
      when(cvTLModelPrimitiveDao.get(eqTo(classifier.id))(any[ExecutionContext])).thenReturn(future(Some(classifier)))
      when(packageService.get(packageId)(user)).thenReturn(future(DCProjectPackageServiceError.AccessDenied.asLeft))

      whenReady(
        cvModelPrimitiveService.getModelPrimitiveWithPackage(classifier.id)(user)
      )(
        _ shouldBe CVTLModelPrimitiveServiceError.AccessDenied(classifier.id).asLeft
      )
    }
  }

}
