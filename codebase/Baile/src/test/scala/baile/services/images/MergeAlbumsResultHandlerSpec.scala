package baile.services.images

import java.time.Instant
import java.util.UUID

import akka.actor.{ ActorRef, Props }
import baile.BaseSpec
import baile.dao.images.PictureDao
import baile.daocommons.WithId
import baile.domain.images._
import baile.domain.job.CortexJobStatus
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.process.JobResultHandler.HandleJobResult
import cortex.api.job.album.common.Tag
import cortex.api.job.album.uploading.{ S3ImagesImportResult, UploadedImage }
import cortex.api.job.common.File
import org.mockito.ArgumentMatchers.{ any, argThat, eq => eqTo }
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

class MergeAlbumsResultHandlerSpec extends BaseSpec with BeforeAndAfterEach {

  private val cortexJobService: CortexJobService = mock[CortexJobService]
  private val jobMetaService: JobMetaService = mock[JobMetaService]
  private val commonService: ImagesCommonService = mock[ImagesCommonService]
  private val pictureDao: PictureDao = mock[PictureDao]

  private val handler: ActorRef = system.actorOf(Props(
    new MergeAlbumsResultHandler(
      conf,
      cortexJobService,
      jobMetaService,
      commonService,
      pictureDao,
      logger
    )
  ))

  private val albumId: String = randomString()
  private val pictureId: String = randomString()
  private val inputAlbumId: String = randomString()
  private val jobId: UUID = UUID.randomUUID()

  private val album = WithId(Album(
    ownerId = UUID.randomUUID(),
    name = randomString(),
    status = AlbumStatus.Uploading,
    `type` = AlbumType.Source,
    labelMode = AlbumLabelMode.Classification,
    created = Instant.now,
    updated = Instant.now,
    inLibrary = true,
    picturesPrefix = randomString(),
    description = None,
    augmentationTimeSpentSummary = None
  ), albumId)

  when(cortexJobService.getJobOutputPath(any[UUID])).thenReturn(future(new RuntimeException("miss")))
  when(cortexJobService.getJobOutputPath(eqTo(jobId))).thenReturn(future(randomPath()))
  when(jobMetaService.readRawMeta(any[UUID], any[String])).thenReturn(future(new RuntimeException("miss")))
  when(jobMetaService.readRawMeta(eqTo(jobId), any[String])).thenReturn(future(S3ImagesImportResult(
    images = Seq(UploadedImage(
      file = Some(File(
        filePath = randomPath("png"),
        fileName = randomPath("png"),
        fileSize = randomInt(2045)
      )),
      tags = Seq(Tag("foo")),
      metadata = Map.empty,
      referenceId = Some(pictureId)
    )),
    failedFiles = Seq.empty
  ).toByteArray))
  when(pictureDao.create(any[Picture])(any[ExecutionContext]))
    .thenReturn(future(randomString()))
  when(pictureDao.createMany(any[Seq[Picture]])(any[ExecutionContext]))
    .thenReturn(future(Seq(randomString())))
  when(commonService.getPictures(any[Seq[String]], any[Boolean]))
    .thenReturn(future(Seq.empty))
  when(commonService.getPictures(argThat[Seq[String]](_.contains(inputAlbumId)), any[Boolean], any[Seq[String]]))
    .thenReturn(future(Seq(WithId(
      entity = Picture(
        albumId = inputAlbumId,
        filePath = randomPath(),
        fileName = randomString(),
        fileSize = Some(randomInt(1024, 102400)),
        caption = None,
        predictedCaption = None,
        tags = Seq.empty,
        predictedTags = Seq.empty,
        meta = Map.empty,
        originalPictureId = None,
        appliedAugmentations = None
      ),
      pictureId
    ))))
  when(commonService.attachPictures(any[String], any[Seq[Picture]])).thenReturn(future(()))
  when(commonService.makeAlbumActive(any[String]))
    .thenReturn(future(new RuntimeException("miss")))
  when(commonService.makeAlbumActive(eqTo(albumId)))
    .thenReturn(future(Some(album)))

  "ImportFromS3ResultHandler#handle" should {
    "handle HandleJobResult message" in {
      handler ! HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(MergeAlbumsResultHandler.Meta(
          albumId = albumId,
          inputAlbumsIds = Seq(inputAlbumId),
          onlyLabelled = randomBoolean()
        ))
      )

      expectMsgType[Unit]
      verify(commonService).makeAlbumActive(eqTo(albumId))
    }
  }
}
