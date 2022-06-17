package baile.services.images

import java.time.Instant
import java.util.UUID

import akka.actor.{ ActorRef, Props }
import baile.BaseSpec
import baile.dao.images.AlbumDao
import baile.daocommons.WithId
import baile.domain.images._
import baile.domain.job.{ CortexJobStatus, CortexJobTimeSpentSummary, CortexTaskTimeInfo, CortexTimeInfo }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.images.ImagesCommonService.AugmentationResultImage
import baile.services.process.JobResultHandler.HandleJobResult
import cortex.api.job.album.augmentation._
import cortex.api.job.album.common.{ Image, Tag, TagArea, TaggedImage }
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

class ImagesAugmentationResultHandlerSpec extends BaseSpec {

  private val cortexJobService: CortexJobService = mock[CortexJobService]
  private val jobMetaService: JobMetaService = mock[JobMetaService]
  private val imagesCommonService: ImagesCommonService = mock[ImagesCommonService]
  private val albumDao: AlbumDao = mock[AlbumDao]

  val handler: ActorRef = system.actorOf(Props(
    new ImagesAugmentationResultHandler(
      cortexJobService,
      imagesCommonService,
      jobMetaService,
      albumDao,
      logger
    )
  ))

  private val jobId: UUID = UUID.randomUUID()
  private val outputAlbumId: String = randomString()
  private val inputAlbumId: String = randomString()

  val album = Album(
    UUID.randomUUID(),
    "random-name",
    AlbumStatus.Saving,
    AlbumType.Source,
    AlbumLabelMode.Classification,
    Instant.now,
    Instant.now,
    true,
    "prefix",
    None,
    None,
    None
  )

  val albumWithId = WithId(album, outputAlbumId)

  val tag: Tag = Tag("label-tag", Some(TagArea(1,2,3,4)))

  val pictureTags = PictureTag("label", Some(PictureTagArea(1, 2, 3, 4)), Some(2d))
  val picture = WithId(
    Picture(
      albumId = inputAlbumId,
      filePath = "file-path",
      fileName = "file-name",
      fileSize = Some(20l),
      tags = Seq(pictureTags),
      caption = None,
      predictedCaption = None,
      predictedTags = Seq.empty,
      meta = Map.empty,
      originalPictureId = None,
      appliedAugmentations = None
    ),
    randomString()
  )

  val image = TaggedImage(
    image = Some(
      Image(
        filePath = "fd391b82-1a1a-414d-a320-a825d0c78605-airplane.jpg",
        referenceId = Some(picture.id)
      )
    ),
    tags = Seq(tag)
  )

  val appliedRotationParams = AppliedAugmentation.GeneralParams.RotationParams(
    AppliedRotationParams(
      angle = 50F,
      resize = true
    )
  )

  val augmentations = Seq(
    AppliedAugmentation(
      generalParams = appliedRotationParams,
      extraParams = Map(randomString() -> randomInt(100), randomString() -> randomInt(200)),
      internalParams = Map(randomString() -> randomInt(100), randomString() -> randomInt(200))
    )
  )

  val augmentedImages = Seq(AugmentedImage(Some(image), augmentations))
  val augmentationResult = AugmentationResult(Seq(image), augmentedImages)
  val augmentationTimeInfo = CortexTimeInfo(
    submittedAt = Instant.now(),
    startedAt = Instant.now(),
    completedAt = Instant.now()
  )
  val cortexTaskTimeInfo = CortexTaskTimeInfo(
    "train",
    augmentationTimeInfo
  )
  val augmentationTimeSpentSummary =  CortexJobTimeSpentSummary(
    2L,
    augmentationTimeInfo,
    Seq(cortexTaskTimeInfo)
  )

  when(imagesCommonService.getAlbum(eqTo(outputAlbumId))) thenReturn future(Some(albumWithId))
  when(cortexJobService.getJobOutputPath(any[UUID])) thenReturn future("path")
  when(jobMetaService.readRawMeta(any[UUID], eqTo("path"))) thenReturn future(augmentationResult.toByteArray)
  when(imagesCommonService.populateAugmentedAlbum(
    eqTo(inputAlbumId),
    eqTo(outputAlbumId),
    eqTo(album.labelMode),
    any[Seq[AugmentationResultImage]]
  )).thenReturn(future(()))
  when(albumDao.update(eqTo(outputAlbumId), any[Album => Album].apply)(any[ExecutionContext]))
    .thenReturn(future(Some(albumWithId)))
  when(cortexJobService.getJobTimeSummary(any[UUID])).thenReturn(future(augmentationTimeSpentSummary))

  "ImagesAugmentationResultHandler#handle" should {
    "handle HandleJobResult message while keeping original images" in {
      handler ! HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(ImagesAugmentationResultHandler.Meta(
          outputAlbumId = outputAlbumId,
          inputAlbumId = inputAlbumId,
          keepOriginalImages = true
        ))
      )
      expectMsgType[Unit]
    }
  }

  "ImagesAugmentationResultHandler#handle" should {
    "handle HandleJobResult message while not keeping original images" in {
      handler ! HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(ImagesAugmentationResultHandler.Meta(
          outputAlbumId = outputAlbumId,
          inputAlbumId = inputAlbumId,
          keepOriginalImages = false
        ))
      )
      expectMsgType[Unit]
    }
  }

  "ImagesAugmentationResultHandler#handle" should {
    "handle HandleJobResult message when cortex job status failed" in {
      handler ! HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Failed,
        rawMeta = Json.toJsObject(ImagesAugmentationResultHandler.Meta(
          outputAlbumId = outputAlbumId,
          inputAlbumId = inputAlbumId,
          keepOriginalImages = false
        ))
      )
      expectMsgType[Unit]
    }
  }

}
