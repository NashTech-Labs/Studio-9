package baile.dao.images

import baile.BaseItSpec
import baile.dao.images.PictureDao.{ HasPredictedTags, HasTags, LabelsAre, PredictedLabelsAre }
import baile.dao.mongo.DockerizedMongoDB
import baile.domain.images.{ Picture, PictureTag }

class PictureDaoItSpec extends BaseItSpec with DockerizedMongoDB {

  private lazy val dao = new PictureDao(database)

  private val firstAlbumId = randomString()
  private val secondAlbumId = randomString()
  private val labelNeedle = randomString()

  private def preparePicture: Picture = Picture(
    albumId = randomOf(firstAlbumId, secondAlbumId),
    filePath = randomPath("png"),
    fileName = randomString(),
    fileSize = Some(randomInt(10240, 1024000)),
    tags = randomOf(Seq.empty, Seq(
      PictureTag(
        label = randomString()
      )
    )),
    caption = None,
    predictedCaption = None,
    predictedTags = Seq.empty,
    meta = Map.empty,
    originalPictureId = None,
    appliedAugmentations = None
  )

  val entities: Seq[Picture] = Seq(
    Picture(
      albumId = firstAlbumId,
      filePath = randomPath("png"),
      fileName = randomString(),
      fileSize = Some(randomInt(10240, 1024000)),
      caption = None,
      predictedCaption = None,
      tags = Seq.empty,
      predictedTags = Seq.empty,
      meta = Map.empty,
      originalPictureId = None,
      appliedAugmentations = None
    ),
    Picture(
      albumId = secondAlbumId,
      filePath = randomPath("png"),
      fileName = randomString(),
      fileSize = Some(randomInt(10240, 1024000)),
      caption = None,
      predictedCaption = None,
      tags = Seq.empty,
      predictedTags = Seq.empty,
      meta = Map.empty,
      originalPictureId = None,
      appliedAugmentations = None
    ),
    Picture(
      albumId = firstAlbumId,
      filePath = randomPath("png"),
      fileName = randomString(),
      fileSize = Some(randomInt(10240, 1024000)),
      tags = Seq(
        PictureTag(
          label = labelNeedle
        )
      ),
      caption = None,
      predictedCaption = None,
      predictedTags = Seq.empty,
      meta = Map.empty,
      originalPictureId = None,
      appliedAugmentations = None
    ),
    Picture(
      albumId = secondAlbumId,
      filePath = randomPath("png"),
      fileName = randomString(),
      fileSize = Some(randomInt(10240, 1024000)),
      predictedTags = Seq(
        PictureTag(
          label = labelNeedle
        )
      ),
      caption = None,
      predictedCaption = None,
      tags = Seq.empty,
      meta = Map.empty,
      originalPictureId = None,
      appliedAugmentations = None
    ),
  ) ++ Range(0, randomInt(10, 100)).map(_ => preparePicture)

  override def beforeAll {
    super.beforeAll()
    dao.createMany(entities)
  }

  "dao.listAll" should {

    "handle HasTags filter" in {
      whenReady(dao.listAll(HasTags)) { result =>
        assert(result.forall(_.entity.tags.nonEmpty))
      }
    }

    "handle HasPredictedTags filter" in {
      whenReady(dao.listAll(HasPredictedTags)) { result =>
        assert(result.forall(_.entity.predictedTags.nonEmpty))
      }
    }

    "handle LabelsAre filter" in {
      whenReady(dao.listAll(LabelsAre(Seq(labelNeedle)))) { result =>
        assert(result.forall(_.entity.tags.exists(_.label == labelNeedle)))
      }
    }

    "handle PredictedLabelsAre filter" in {
      whenReady(dao.listAll(PredictedLabelsAre(Seq(labelNeedle)))) { result =>
        assert(result.forall(_.entity.predictedTags.exists(_.label == labelNeedle)))
      }
    }

  }

}
