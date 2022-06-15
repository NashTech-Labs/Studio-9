package cortex.jobmaster.orion.service.domain

import java.util.UUID

import cortex.api.job.album.augmentation._
import cortex.jobmaster.jobs.job.DataAugmentationJob
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.fixtures.UploadedImages
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.testkit.{ FutureTestUtils, WithEventually, WithS3AndLocalScheduler }
import org.scalamock.scalatest.MockFactory
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global

class DataAugmentationServiceTest extends FlatSpec
  with FutureTestUtils
  with WithEventually
  with WithS3AndLocalScheduler
  with UploadedImages
  with MockFactory
  with SettingsModule {

  lazy val s3AccessParams = S3AccessParams(
    bucket      = baseBucket,
    accessKey   = accessKey,
    secretKey   = secretKey,
    region      = "",
    endpointUrl = Some(fakeS3Endpoint)
  )

  lazy val daJob = new DataAugmentationJob(taskScheduler, dataAugmentationConfig)

  lazy val daService = new DataAugmentationService(daJob, s3AccessParams)

  private def callAugmentationService(augmentations: Seq[RequestedAugmentation], bloatFactor: Int) = {
    val request = AugmentationRequest(
      images                = taggedImages,
      filePathPrefix        = albumPath,
      augmentations         = augmentations,
      bloatFactor           = Some(bloatFactor),
      targetPrefix          = s"baile/data/albums/${UUID.randomUUID.toString}",
      includeOriginalImages = true
    )

    daService.augment(jobId, request).await()
  }

  "DataAugmentationService" should "augment unlabeled images" in {

    val rotationRequestParams = RequestedAugmentation.Params.RotationParams(
      RotationRequestParams(
        angles = Seq(10, 30, 65, 110),
        resize = false
      )
    )

    val blurringRequestParams = RequestedAugmentation.Params.BlurringParams(
      BlurringRequestParams(
        sigmaList = Seq(0.5F, 3.4F, 4)
      )
    )

    val saltPepperRequestParams = RequestedAugmentation.Params.SaltPepperParams(
      SaltPepperRequestParams(
        knockoutFractions = Seq(0.08F, 0.9F),
        pepperProbability = 0.3F
      )
    )

    val augmentations = Seq(
      saltPepperRequestParams,
      blurringRequestParams,
      rotationRequestParams
    ).map { params =>
      RequestedAugmentation(
        bloatFactor = 1,
        params      = params
      )
    }

    val (daResult, _) = callAugmentationService(augmentations, bloatFactor = 3)

    daResult.augmentedImages.size shouldBe taggedImages.size * 3
    daResult.originalImages.size shouldBe taggedImages.size
    Seq("SaltPepper", "Blurring", "Rotation").foreach(daResult.pipelineTimings should contain key _)
  }

  it should "run with other augmentation params" in {
    val shearingRequestParams = RequestedAugmentation.Params.ShearingParams(
      ShearingRequestParams(
        angles = Seq(15F, 30F),
        resize = true
      )
    )

    val noisingRequestParams = RequestedAugmentation.Params.NoisingParams(
      NoisingRequestParams(
        noiseSignalRatios = Seq(0.15F, 0.3F)
      )
    )

    val zoomInRequestParams = RequestedAugmentation.Params.ZoomInParams(
      ZoomInRequestParams(
        magnifications = Seq(1.2F, 1.5F),
        resize         = true
      )
    )

    val zoomOutRequestParams = RequestedAugmentation.Params.ZoomOutParams(
      ZoomOutRequestParams(
        shrinkFactors = Seq(0.2F, 0.5F),
        resize        = false
      )
    )

    val occlusionRequestParams = RequestedAugmentation.Params.OcclusionParams(
      OcclusionRequestParams(
        occAreaFractions = Seq(0.05F, 0.1F, 0.25F),
        mode             = OcclusionMode.ZERO,
        isSarAlbum       = false,
        tarWinSize       = 32
      )
    )

    val translationRequestParams = RequestedAugmentation.Params.TranslationParams(
      TranslationRequestParams(
        translateFractions = Seq(0.1F, 0.2F, 0.3F),
        mode               = TranslationMode.CONSTANT,
        resize             = true
      )
    )

    val mirroringRequestParams = RequestedAugmentation.Params.MirroringParams(
      MirroringRequestParams(
        axesToFlip = Seq(MirroringAxisToFlip.HORIZONTAL, MirroringAxisToFlip.VERTICAL, MirroringAxisToFlip.VERTICAL)
      )
    )

    val croppingRequestParams = RequestedAugmentation.Params.CroppingParams(
      CroppingRequestParams(
        cropAreaFractions = Seq(0.24F, 0.84F),
        cropsPerImage     = 1,
        resize            = false
      )
    )

    val photometricDistortRequestParams = RequestedAugmentation.Params.PhotometricDistortParams(
      PhotometricDistortRequestParams(
        alphaBounds = Some(PhotometricDistortAlphaBounds(0.5F, 1.5F)),
        deltaMax    = 18F
      )
    )

    val augmentations = Seq(
      shearingRequestParams,
      noisingRequestParams,
      zoomInRequestParams,
      zoomOutRequestParams,
      occlusionRequestParams,
      translationRequestParams,
      mirroringRequestParams,
      croppingRequestParams,
      photometricDistortRequestParams
    ).map { params =>
      RequestedAugmentation(
        bloatFactor = 1,
        params      = params
      )
    }

    val bloatFactor = 10

    val (daResult, _) = callAugmentationService(augmentations, bloatFactor)

    daResult.augmentedImages.size shouldBe taggedImages.size * bloatFactor.min(augmentations.size)
    daResult.originalImages.size shouldBe taggedImages.size
    Seq("Shearing", "Noising", "ZoomIn", "ZoomOut", "Occlusion", "Translation", "Mirroring", "Cropping", "PhotometricDistort")
      .foreach(daResult.pipelineTimings should contain key _)
  }

  private def jobId = {
    UUID.randomUUID().toString
  }

}
