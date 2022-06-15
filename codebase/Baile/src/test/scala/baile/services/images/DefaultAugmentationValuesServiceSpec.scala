package baile.services.images

import baile.BaseSpec
import baile.services.images.util.TestData.AugmentationRequestsData
import com.typesafe.config.{ Config, ConfigFactory }

class DefaultAugmentationValuesServiceSpec extends BaseSpec {

  private val defaultConfig: Config = ConfigFactory.load("default-values")
  private val service = new DefaultAugmentationValuesService(defaultConfig.getConfig("augmentation-default-values"))

  "DefaultAugmentationValuesService#getDefaultAugmentationValues" should {

    "correctly fetch the default augmentation values" in {
      assert(service.getDefaultAugmentationValues == AugmentationRequestsData)
    }

  }

}
