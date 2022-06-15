package baile.dao.images

import baile.BaseSpec
import baile.dao.images.util.PictureTestData._
import org.mongodb.scala.{ Document, MongoDatabase }
import org.scalatest.prop.TableDrivenPropertyChecks

class PictureDaoSpec extends BaseSpec with TableDrivenPropertyChecks {

  val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
  val pictureDao: PictureDao = new PictureDao(mockedMongoDatabase)

  val testData = Table(
    ("clue", "data1", "data2"),
    ("data is correct", PictureEntity, PictureDoc),
    ("optional fields are None", PictureEntityWithNone, PictureDocWithNone),
    ("picture contains meta", PictureEntityWithMeta, PictureDocWithMeta),
    ("picture contains rotation augmentation", RotatedPictureEntity, RotatedPictureDoc),
    ("picture contains occlusion augmentation", OccludedPictureEntity, OccludedPictureDoc),
    ("picture contains shearing augmentation", ShearedPictureEntity, ShearedPictureDoc),
    ("picture contains cropping augmentation", CroppedPictureEntity, CroppedPictureDoc),
    ("picture contains mirroring augmentation", MirroredPictureEntity, MirroredPictureDoc),
    ("picture contains blurring augmentation", BlurredPictureEntity, BlurredPictureDoc),
    ("picture contains salt pepper augmentation", SaltPepperPictureEntity, SaltPepperPictureDoc),
    ("picture contains photometric distortion augmentation",
      PhotometricDistortPictureEntity,
      PhotometricDistortPictureDoc
    ),
    ("picture contains zoom in augmentation", ZoomedInPictureEntity, ZoomedInPictureDoc),
    ("picture contains zoom out augmentation", ZoomedOutPictureEntity, ZoomedOutPictureDoc),
    ("picture contains noising augmentation", NoisingPictureEntity, NoisingPictureDoc),
    ("picture contains translation augmentation", TranslationPictureEntity, TranslationPictureDoc)
  )

  "PictureDao" when {
    forAll(testData) { (clue, data1, data2) =>
      s"$clue" should {
        "be able to convert entity into document" in {
          val action = pictureDao.entityToDocument(data1)
          action shouldBe data2
        }

        "be able to convert document into entity" in {
          val action = pictureDao.documentToEntity(data2)
          action.success.value shouldBe data1
        }
      }
    }

    "document is empty" should {
      "not be able to convert document into entity" in {
        val result = pictureDao.documentToEntity(Document())
        assert(result.isFailure)
      }
    }
  }

}
