package baile.services.images

import java.time.Instant
import java.util.UUID

import akka.actor.{ ActorRef, Props }
import baile.BaseSpec
import baile.dao.images.{ AlbumDao, PictureDao }
import baile.daocommons.WithId
import baile.domain.images._
import baile.domain.job.CortexJobStatus
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.process.JobResultHandler.HandleJobResult
import cortex.api.job.album.uploading.S3VideoImportResult
import cortex.api.job.common.File
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

class VideoImportFromS3ResultHandlerSpec extends BaseSpec with BeforeAndAfterEach {

  private val cortexJobService: CortexJobService = mock[CortexJobService]
  private val jobMetaService: JobMetaService = mock[JobMetaService]
  private val albumDao: AlbumDao = mock[AlbumDao]
  private val pictureDao: PictureDao = mock[PictureDao]
  private val imagesCommonService: ImagesCommonService = mock[ImagesCommonService]

  private val handler: ActorRef = system.actorOf(Props(
    new VideoImportFromS3ResultHandler(
      cortexJobService,
      jobMetaService,
      imagesCommonService,
      albumDao,
      pictureDao,
      logger
    )
  ))

  private val albumId: String = randomString()
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
  when(jobMetaService.readRawMeta(eqTo(jobId), any[String])).thenReturn(future(S3VideoImportResult(
    imageFiles = Seq(File(
      filePath = randomPath("png"),
      fileName = randomPath("png"),
      fileSize = randomInt(2045)
    )),
    videoFile = Some(File(
      filePath = randomPath("mp4"),
      fileName = randomPath("mp4"),
      fileSize = randomInt(20450, 204800)
    )),
    videoFrameRate = randomInt(10, 60),
    videoHeight = 1080,
    videoWidth = 1920
  ).toByteArray))
  when(pictureDao.create(any[Picture])(any[ExecutionContext]))
    .thenReturn(future(randomString()))
  when(pictureDao.createMany(any[Seq[Picture]])(any[ExecutionContext]))
    .thenReturn(future(Seq(randomString())))
  when(albumDao.update(any[String], any[Album => Album].apply)(any[ExecutionContext]))
    .thenReturn(future(new RuntimeException("miss")))
  when(albumDao.update(eqTo(albumId), any[Album => Album].apply)(any[ExecutionContext]))
    .thenReturn(future(Some(album)))

  "VideoImportFromS3ResultHandler#handle" should {
    "handle HandleJobResult message" in {
      handler ! HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(VideoImportFromS3ResultHandler.Meta(
          albumId = albumId,
          frameRateDivider = randomInt(5)
        ))
      )

      expectMsgType[Unit]
    }
  }
}
