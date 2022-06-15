package cortex.task

import cortex.JsonSupport.SnakeJson
import cortex.task.computer_vision.AutoAugmentation
import cortex.task.computer_vision.AutoAugmentation._
import cortex.task.data_augmentation.DataAugmentationParams.{ AppliedAugmentationInfo, AugmentationType, Tag, TransformImagesResult, TransformResult }
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class AutoAugmentationResultSerializationTest extends FlatSpec {
  "AutoAugmentationResult" should "be deserialized from json string properly" in {

    val serializedResult =
      """{"transform_result": {"image_paths": ["f1a0f2d5-921d-4861-b335-68737b5ef0d9"], "image_sizes": [1],""" +
        """"reference_ids": ["5bf50d4bc9e77c0001cb5ffc"], "tags": [[{"label": "2s1_gun"}]],""" +
        """"augmentations": [{"name": "noising", "arg": 0.15000000596046448, "resize": null, "mode": null, "extra": {}},""" +
        """{"name": "zoom_out", "arg": 0.20000000298023224, "resize": null, "mode": null, "extra": {}},""" +
        """{"name": "salt_pepper", "arg": 0.30000001192092896, "resize": null, "mode": null, "extra": {}},""" +
        """{"name": "translation", "arg": 16, "resize": null, "mode": null, "extra": {}},""" +
        """{"name": "unchanged", "arg": 1.0, "resize": null, "mode": null, "extra": {}},""" +
        """{"name": "zoom_in", "arg": 2, "resize": null, "mode": null, "extra": {}},""" +
        """{"name": "blurring", "arg": 4, "resize": null, "mode": null, "extra": {}},""" +
        """{"name": "mirroring", "arg": 0, "resize": null, "mode": null, "extra": {}},""" +
        """{"name": "rotation", "arg": 90, "resize": null, "mode": null, "extra": {}},""" +
        """{"name": "cropping", "arg": 0.36000001430511475, "resize": null, "mode": null, "extra": {}},""" +
        """{"name": "shearing", "arg": 30, "resize": null, "mode": null, "extra": {}},""" +
        """{"name": "translation", "arg": 27, "resize": null, "mode": null, "extra": {}}]},""" +
        """"augmentation_summary": {"noising": 7, "zoom_out": 7, "salt_pepper": 7, "translation": 28, "unchanged": 13, "zoom_in": 7,""" +
        """"blurring": 6, "mirroring": 6, "rotation": 7, "cropping": 6, "shearing": 7}}"""

    val expectedResult = AutoAugmentationResult(
      transformResult     = TransformImagesResult(
        imagePaths    = Seq("f1a0f2d5-921d-4861-b335-68737b5ef0d9"),
        referenceIds  = Seq("5bf50d4bc9e77c0001cb5ffc"),
        imageSizes    = Seq(1),
        tags          = Seq(Seq(Tag(label = "2s1_gun"))),
        augmentations = Seq(
          AppliedAugmentationInfo(AugmentationType.Noising, 0.15000000596046448F, None, None, Map(), None, None, None, None, None, None),
          AppliedAugmentationInfo(AugmentationType.ZoomOut, 0.20000000298023224F, None, None, Map(), None, None, None, None, None, None),
          AppliedAugmentationInfo(AugmentationType.SaltPepper, 0.30000001192092896F, None, None, Map(), None, None, None, None, None, None),
          AppliedAugmentationInfo(AugmentationType.Translation, 16F, None, None, Map(), None, None, None, None, None, None),
          AppliedAugmentationInfo(AugmentationType.Unchanged, 1.0F, None, None, Map(), None, None, None, None, None, None),
          AppliedAugmentationInfo(AugmentationType.ZoomIn, 2F, None, None, Map(), None, None, None, None, None, None),
          AppliedAugmentationInfo(AugmentationType.Blurring, 4F, None, None, Map(), None, None, None, None, None, None),
          AppliedAugmentationInfo(AugmentationType.Mirroring, 0F, None, None, Map(), None, None, None, None, None, None),
          AppliedAugmentationInfo(AugmentationType.Rotation, 90F, None, None, Map(), None, None, None, None, None, None),
          AppliedAugmentationInfo(AugmentationType.Cropping, 0.36000001430511475F, None, None, Map(), None, None, None, None, None, None),
          AppliedAugmentationInfo(AugmentationType.Shearing, 30F, None, None, Map(), None, None, None, None, None, None),
          AppliedAugmentationInfo(AugmentationType.Translation, 27F, None, None, Map(), None, None, None, None, None, None)
        )
      ),
      augmentationSummary = Map(
        AugmentationType.Noising -> 7,
        AugmentationType.ZoomOut -> 7,
        AugmentationType.SaltPepper -> 7,
        AugmentationType.Translation -> 28,
        AugmentationType.Unchanged -> 13,
        AugmentationType.ZoomIn -> 7,
        AugmentationType.Blurring -> 6,
        AugmentationType.Mirroring -> 6,
        AugmentationType.Rotation -> 7,
        AugmentationType.Cropping -> 6,
        AugmentationType.Shearing -> 7
      )
    )

    SnakeJson.parse(serializedResult).as[AutoAugmentationResult](AutoAugmentation.autoAugmentationResultReads) shouldBe expectedResult
  }
}
