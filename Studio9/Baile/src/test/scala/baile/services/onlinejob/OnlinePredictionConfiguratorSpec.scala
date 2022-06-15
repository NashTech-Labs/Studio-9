package baile.services.onlinejob

import baile.BaseSpec
import baile.domain.common.S3Bucket.IdReference
import baile.domain.images.AlbumLabelMode.Classification
import baile.domain.images.{ Album, AlbumType }
import baile.domain.usermanagement.User
import baile.services.argo.ArgoService
import baile.services.common.S3BucketService
import baile.services.cv.model.{ CVModelCommonService, CVModelService }
import baile.services.cv.model.CVModelService.CVModelServiceError
import baile.services.images.{ AlbumService, ImagesCommonService }
import baile.services.onlinejob.util.TestData._
import baile.services.usermanagement.util.TestData.SampleUser
import baile.services.onlinejob.OnlinePredictionConfigurator.OnlinePredictionConfiguratorError
import baile.services.onlinejob.exceptions.UnexpectedResponseException
import cats.implicits._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.when

import scala.util.Try

class OnlinePredictionConfiguratorSpec extends BaseSpec {

  implicit val user: User = SampleUser
  val mockedAlbumService: AlbumService = mock[AlbumService]
  val mockedImagesCommonService: ImagesCommonService = mock[ImagesCommonService]
  val mockedCVModelCommonService: CVModelCommonService = mock[CVModelCommonService]
  val mockedArgoService: ArgoService = mock[ArgoService]
  val mockedCVModelService: CVModelService = mock[CVModelService]
  val mockedS3BucketService: S3BucketService = mock[S3BucketService]
  val configurator = new OnlinePredictionConfigurator(
    conf,
    mockedArgoService,
    mockedImagesCommonService,
    mockedCVModelCommonService,
    mockedAlbumService,
    mockedCVModelService,
    mockedS3BucketService
  )

  "OnlinePredictionConfigurator#configure" should {
    "return the success response" in {
      when(mockedS3BucketService.dereferenceBucket(
        IdReference(OnlinePredictionCreateOptionsSample.bucketId))) thenReturn future(AccessOptionsSample.asRight)
      when(mockedImagesCommonService.getImagesPathPrefix(any[Album])) thenReturn "imagePath"
      when(mockedCVModelService.get("modelId")) thenReturn future(CVModelSample.asRight)
      when(mockedCVModelCommonService.getCortexModelId(CVModelSample)) thenReturn Try("cortexId")
      when(mockedAlbumService.create(
        "outputAlbumName",
        Classification,
        AlbumType.Derived,
        true,
        SampleUser.id
      )) thenReturn future(AlbumSample)

      when(mockedArgoService.setConfigValue(
        any[String],
        any[String],
        any[String],
        any[List[String]]
      )) thenReturn future(ConfigSettingSample)
      val response = configurator.configure(OnlinePredictionCreateOptionsSample)

      whenReady(response) { result =>
        result shouldBe Right(OnlinePredictionOptionsSample)
      }
    }

    "return the error response when ModelNotFound" in {
      when(mockedS3BucketService.dereferenceBucket(
        IdReference(OnlinePredictionCreateOptionsSample.bucketId))) thenReturn future(AccessOptionsSample.asRight)
      when(mockedCVModelService.get("modelId")) thenReturn future(CVModelServiceError.ModelNotFound.asLeft)
      val response = configurator.configure(OnlinePredictionCreateOptionsSample)

      whenReady(response) { result =>
        result shouldBe Left(OnlinePredictionConfiguratorError.ModelNotFound)
      }
    }

    "return the error response when unexpected error comes" in {
      when(mockedS3BucketService.dereferenceBucket(
        IdReference(OnlinePredictionCreateOptionsSample.bucketId))) thenReturn future(AccessOptionsSample.asRight)
      when(mockedCVModelService.get("modelId")) thenReturn future(CVModelServiceError.CantDeleteRunningModel.asLeft)
      val response = configurator.configure(OnlinePredictionCreateOptionsSample)

      whenReady(response.failed) { result =>
        result shouldBe a[UnexpectedResponseException]
      }
    }
  }

}
