package baile.services.cv.model

import java.time.Instant

import baile.BaseSpec
import baile.dao.cv.model.CVModelDao
import baile.dao.images.{ AlbumDao, PictureDao }
import baile.dao.table.TableDao
import baile.daocommons.WithId
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.SortBy
import baile.domain.cv.model.CVModelType.TLConsumer.Localizer
import baile.domain.cv.model.{ CVModel, CVModelStatus, CVModelType }
import baile.domain.images._
import baile.domain.table.{ Table, TableType }
import baile.services.cv.CVTLModelPrimitiveService
import baile.services.images.ImagesCommonService.AugmentationResultImage
import baile.services.images.util.ImagesRandomGenerator._
import baile.services.images.{ AlbumService, ImagesCommonService }
import baile.services.table.TableService
import baile.services.table.util.TableRandomGenerator
import baile.services.usermanagement.util.TestData
import cats.implicits._
import cortex.api.job.album.augmentation.RequestedAugmentation
import cortex.api.job.album.common.{ Image, Tag, TagArea }
import cortex.api.job.computervision.{
  PredictedImage,
  PredictedTag,
  ProbabilityPredictionAreaColumns,
  ProbabilityPredictionTableSchema
}
import cortex.api.job.table.ProbabilityClassColumn
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.{ verify, when }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class CVModelCommonServiceSpec extends BaseSpec {

  val albumDao = mock[AlbumDao]
  val tableDao = mock[TableDao]
  val modelDao = mock[CVModelDao]
  val pictureDao = mock[PictureDao]
  val albumService = mock[AlbumService]
  val imagesCommonService = mock[ImagesCommonService]
  val tableService = mock[TableService]
  val cvModelPrimitiveService = mock[CVTLModelPrimitiveService]

  val service = new CVModelCommonService(
    albumDao = albumDao,
    modelDao = modelDao,
    pictureDao = pictureDao,
    tableDao = tableDao,
    albumService = albumService,
    imagesCommonService = imagesCommonService,
    tableService = tableService,
    cvModelPrimitiveService = cvModelPrimitiveService,
    albumPopulationBatchSize = 1000,
    albumPopulationParallelismLevel = 5
  )

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
  val albumName = "album"
  val album =
    Album(
      ownerId = user.id,
      name = albumName,
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
  val albumId = randomString()
  val albumWithId = WithId(album, albumId)
  val emptyAlbumId = randomString()

  val outputAlbumId = randomString()
  val pictures = (1 to 10).map { index =>
    WithId(
      Picture(
        albumId = albumId,
        filePath = s"folder/$index",
        fileName = s"$index.png",
        fileSize = Some(20l),
        caption = Some(s"This is index $index"),
        predictedCaption = None,
        tags = Seq(
          PictureTag(
            label = "label1",
            area = Some(PictureTagArea(1, 2, 3, 4)),
            confidence = None
          ),
          PictureTag(
            label = "label2",
            area = Some(PictureTagArea(3, 4, 5, 6)),
            confidence = None
          )
        ),
        predictedTags = Seq.empty,
        meta = Map.empty,
        originalPictureId = None,
        appliedAugmentations = None
      ),
      randomString()
    )
  }
  val predictedImages = pictures.map { case WithId(picture, id) =>
    PredictedImage(
      image = Some(Image(picture.filePath, Some(id))),
      predictedTags = picture.tags.map { tag =>
        PredictedTag(Some(Tag(tag.label, Some(TagArea(1, 2, 3, 4)))))
      }
    )
  }

  val model = CVModelRandomGenerator.randomModel(
    CVModelStatus.Active
  )

  val failedModel = model.copy(id = randomString())

  when(imagesCommonService.countPictures(eqTo(albumWithId.id), any[Boolean])).thenReturn(future(10))
  when(imagesCommonService.countPictures(eqTo(emptyAlbumId), any[Boolean])).thenReturn(future(0))
  when(albumDao.create(any[String => Album])(any[ExecutionContext])).thenAnswer { invocation =>
    val prepareAlbum = invocation.getArgument[String => Album](0)
    future(WithId(prepareAlbum(albumId), albumId))
  }
  when(imagesCommonService.getPictures(eqTo(albumId), eqTo(false))).thenReturn(future(pictures))
  when(imagesCommonService.convertCortexTagToPictureTag(
    any[PredictedTag],
    any[AlbumLabelMode]
  )).thenReturn(Try(PictureTag("")))
  when(pictureDao.createMany(any[Seq[Picture]])(any[ExecutionContext])).thenReturn(future(Seq.empty))
  when(pictureDao.list(any[Filter], eqTo(1), eqTo(1000), any[Option[SortBy]])(any[ExecutionContext]))
    .thenReturn(future(Seq.empty))
  when(modelDao.update(
    eqTo(model.id),
    any[CVModel => CVModel].apply
  )(any[ExecutionContext])).thenReturn(future(Some(model)))
  when(modelDao.update(
    eqTo(failedModel.id),
    any[CVModel => CVModel].apply
  )(any[ExecutionContext])).thenReturn(future(Some(failedModel)))
  when(imagesCommonService.countUserAlbums(any[String], eqTo(None), eqTo(user.id))).thenReturn(future(0))
  when(imagesCommonService.getAlbumMandatory(outputAlbumId)).thenReturn(future(randomAlbum()))

  "CVModelCommonService#validatePicturesCount" should {

    "return unit when there are pictures in album" in {
      whenReady(
        service.validatePicturesCount(albumWithId.id, "no pictures found")
      )(_ shouldBe ().asRight)
    }

    "return error when there are no pictures in album" in {
      whenReady(
        service.validatePicturesCount(emptyAlbumId, "no pictures found", false)
      )(_ shouldBe "no pictures found".asLeft)
    }

  }

  "CVModelCommonService#createOutputAlbum" should {
    "create album" in {
      whenReady(service.createOutputAlbum(
        picturesPrefix = album.picturesPrefix,
        namePrefix = album.name,
        labelMode = album.labelMode,
        albumType = album.`type`,
        inputVideo = Some(Video(
          filePath = video.filePath,
          fileSize = video.fileSize,
          fileName = video.fileName,
          frameRate = video.frameRate,
          frameCaptureRate = video.frameCaptureRate,
          height = video.height,
          width = video.width
        )),
        inLibrary = album.inLibrary,
        albumStatus = album.status,
        userId = user.id
      )) { result =>
        result shouldBe albumWithId.copy(
          entity = album.copy(
            video = result.entity.video,
            created = result.entity.created,
            updated = result.entity.updated
          )
        )
      }
    }
  }

  "CVModelCommonService#createAutoDASampleAlbum" should {
    "create album for automated DA" in {
      val createdAlbum = randomAlbum()
      when(albumService.create(
        name = eqTo(s"$albumName automated DA sample"),
        labelMode = eqTo(album.labelMode),
        albumType = eqTo(AlbumType.Source),
        inLibrary = eqTo(false),
        ownerId = eqTo(user.id)
      )).thenReturn(future(createdAlbum))

      whenReady(service.createAutoDASampleAlbum(
        inputAlbum = album,
        userId = user.id
      ))(_ shouldBe createdAlbum)
    }
  }

  "CVModelCommonService#populateOutputAlbum" should {

    "convert predicted images to pictures and insert them in storage" in {
      whenReady(service.populateOutputAlbumIfNeeded(
        inputAlbumId = albumId,
        outputAlbumId = Some(outputAlbumId),
        predictedImages = predictedImages
      )) { _ =>
        verify(pictureDao).createMany(any[Seq[Picture]])(any[ExecutionContext])
      }

    }

  }

  "CVModelCommonService#updateModelStatus" should {

    "set model status" in {
      whenReady(service.updateModelStatus(model.id, CVModelStatus.Active)) { _ =>
        verify(modelDao).update(eqTo(model.id), any[CVModel => CVModel].apply)(any[ExecutionContext])
      }
    }

  }

  "CVModelCommonService#buildAugmentationParams" should {

    "convert cortex requested augmentation representation to domain augmentation params" in {

      val requestedAugmentations = {
        import cortex.api.job.album.augmentation.RequestedAugmentation.Params
        import cortex.api.job.album.augmentation._
        List(
          Params.BlurringParams(BlurringRequestParams(Seq(0.42f))),
          Params.CroppingParams(CroppingRequestParams(Seq(0.42f), 1, true)),
          Params.MirroringParams(MirroringRequestParams(Seq(MirroringAxisToFlip.BOTH))),
          Params.NoisingParams(NoisingRequestParams(Seq(0.42f))),
          Params.OcclusionParams(OcclusionRequestParams(Seq(0.42f), OcclusionMode.BACKGROUND, true, 2)),
          Params.PhotometricDistortParams(PhotometricDistortRequestParams(
            Some(PhotometricDistortAlphaBounds(0.2f, 0.3f)), 0.42f)
          ),
          Params.RotationParams(RotationRequestParams(Seq(0.42f), true)),
          Params.SaltPepperParams(SaltPepperRequestParams(Seq(0.42f), 0.42f)),
          Params.ShearingParams(ShearingRequestParams(Seq(0.42f), true)),
          Params.TranslationParams(TranslationRequestParams(Seq(0.42f), TranslationMode.CONSTANT, true)),
          Params.ZoomInParams(ZoomInRequestParams(Seq(0.42f), true)),
          Params.ZoomOutParams(ZoomOutRequestParams(Seq(0.42f), true))
        )
      }

      val expectedParams = {
        import baile.domain.images.augmentation._

        List(
          BlurringParams(Seq(0.42f), 16),
          CroppingParams(Seq(0.42f), 1, true, 16),
          MirroringParams(Seq(MirroringAxisToFlip.Both), 16),
          NoisingParams(Seq(0.42f), 16),
          OcclusionParams(Seq(0.42f), OcclusionMode.Background, true, 2, 16),
          PhotometricDistortParams(PhotometricDistortAlphaBounds(0.2f, 0.3f), 0.42f, 16),
          RotationParams(Seq(0.42f), true, 16),
          SaltPepperParams(Seq(0.42f), 0.42f, 16),
          ShearingParams(Seq(0.42f), true, 16),
          TranslationParams(Seq(0.42f), TranslationMode.Constant, true, 16),
          ZoomInParams(Seq(0.42f), true, 16),
          ZoomOutParams(Seq(0.42f), true, 16)
        )
      }

      requestedAugmentations.map { params =>
        service.buildAugmentationParams(RequestedAugmentation(16, params))
      } shouldBe expectedParams

    }

  }

  "CVModelCommonService#populateSampleDAAlbum" should {

    "populate sample DAA album" in {
      when(imagesCommonService.getAlbum(albumId)) thenReturn future(Some(albumWithId))
      when(imagesCommonService.populateAugmentedAlbum(
        eqTo(albumId),
        eqTo(albumId),
        eqTo(album.labelMode),
        any[Seq[AugmentationResultImage]]
      )).thenReturn(future(()))
      whenReady(service.populateSampleDAAlbum(
        inputAlbumId = albumId,
        sampleDAAlbumId = Some(albumId),
        augmentedImages = Seq()
      )) { _ =>
        verify(imagesCommonService).populateAugmentedAlbum(albumId, albumId, album.labelMode, Seq.empty)
      }
    }

  }

  "CVModelCommonService#updatePredictionTableColumnsAndCalculateStatistics" should {

    "update prediction columns for model type localizer" in {
      val table = TableRandomGenerator.randomTable()
      when(tableService.loadTableMandatory(table.id)) thenReturn future(table)
      when(tableService.updateTable(eqTo(table.id), any[Table => Table].apply)) thenReturn
        future(table)
      when(tableService.calculateColumnStatistics(
        eqTo(table.id),
        any(),
        eqTo(user.id)
      )) thenReturn Future.unit
      whenReady(service.updatePredictionTableColumnsAndCalculateStatistics(
        Some(table.id),
        probabilityPredictionTableSchema = Some(ProbabilityPredictionTableSchema(
          probabilityColumns = Seq(ProbabilityClassColumn(className = "class A", columnName = "column1")),
          imageFileNameColumnName = "fileName",
          areaColumns = Some(ProbabilityPredictionAreaColumns("x_min", "x_max", "y_min", "y_max"))
        )),
        modelType = CVModelType.TL(Localizer("opId"), "FE"),
        userId = user.id
      )) { _ =>
        verify(tableService).updateTable(eqTo(table.id), any[Table => Table].apply)
      }
    }

  }

  "CVModelCommonService#createPredictionTable" should {

    "create prediction Table " in {
      val createdTable = TableRandomGenerator.randomTable()
      when(tableDao.count(
        any[Filter]
      )(any[ExecutionContext])).thenReturn(future(0))
      when(tableService.createEmptyTable(
        name = Some(s"${ createdTable.entity.name } probability prediction"),
        tableType = TableType.Derived,
        columns = Seq.empty,
        inLibrary = false,
        user = user
      )).thenReturn(future(createdTable))
      whenReady(service.createPredictionTable(
        baseTableName = createdTable.entity.name
      )(user))(_ shouldBe createdTable)
    }

  }

  "CVModelCommonService#createPredictionTables" should {

    "create prediction tables when model type is not decoder" in {
      val modelName = "model-name"
      val createdTrainTable = TableRandomGenerator.randomTable(name = s"$modelName probability prediction")
      val createdTestTable = TableRandomGenerator.randomTable(name = s"$modelName test probability prediction")

      when(tableDao.count(
        any[Filter]
      )(any[ExecutionContext])).thenReturn(future(0))
      when(tableService.createEmptyTable(
        name = Some(createdTrainTable.entity.name),
        tableType = TableType.Derived,
        columns = Seq.empty,
        inLibrary = false,
        user = user
      )).thenReturn(future(createdTrainTable))
      when(tableService.createEmptyTable(
        name = Some(createdTestTable.entity.name),
        tableType = TableType.Derived,
        columns = Seq.empty,
        inLibrary = false,
        user = user
      )).thenReturn(future(createdTestTable))

      whenReady(service.createPredictionTables(
        modelName = modelName,
        tlConsumer = CVModelType.TLConsumer.Localizer("localizer"),
        withTestTable = true
      )(user))(_ shouldBe((Some(createdTrainTable), Some(createdTestTable))))
    }

    "not create tables when model type is decoder" in {
      val modelName = "model-name"

      whenReady(service.createPredictionTables(
        modelName = modelName,
        tlConsumer = CVModelType.TLConsumer.Decoder("decoder"),
        withTestTable = true
      )(user))(_ shouldBe((None, None)))
    }

    "create only table for train tables when withTestTable = false" in {
      val modelName = "model-name"
      val createdTrainTable = TableRandomGenerator.randomTable(name = s"$modelName probability prediction")

      when(tableDao.count(
        any[Filter]
      )(any[ExecutionContext])).thenReturn(future(0))
      when(tableService.createEmptyTable(
        name = Some(createdTrainTable.entity.name),
        tableType = TableType.Derived,
        columns = Seq.empty,
        inLibrary = false,
        user = user
      )).thenReturn(future(createdTrainTable))

      whenReady(service.createPredictionTables(
        modelName = modelName,
        tlConsumer = CVModelType.TLConsumer.Localizer("localizer"),
        withTestTable = false
      )(user))(_ shouldBe((Some(createdTrainTable), None)))
    }

  }

}
