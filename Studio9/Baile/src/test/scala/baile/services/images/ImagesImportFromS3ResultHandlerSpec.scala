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
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext
import scala.util.Success

class ImagesImportFromS3ResultHandlerSpec extends BaseSpec {

  private val cortexJobService: CortexJobService = mock[CortexJobService]
  private val jobMetaService: JobMetaService = mock[JobMetaService]
  private val pictureDao: PictureDao = mock[PictureDao]
  private val imagesCommonService: ImagesCommonService = mock[ImagesCommonService]

  private val handler: ActorRef = system.actorOf(Props(
    new ImagesImportFromS3ResultHandler(
      cortexJobService,
      imagesCommonService,
      jobMetaService,
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
  when(imagesCommonService.getAlbum(eqTo(albumId))).thenReturn(future(Some(album)))
  when(imagesCommonService.convertCortexTagToPictureTag(any[Tag], any[AlbumLabelMode], any[Option[Double]]))
    .thenReturn(Success(PictureTag("foo")))
  when(jobMetaService.readRawMeta(any[UUID], any[String])).thenReturn(future(new RuntimeException("miss")))
  when(jobMetaService.readRawMeta(eqTo(jobId), any[String])).thenReturn(future(S3ImagesImportResult(
    images = Seq(UploadedImage(
      file = Some(File(
        filePath = randomPath("png"),
        fileName = randomPath("png"),
        fileSize = randomInt(2045)
      )),
      tags = Seq(Tag("foo")),
      metadata = Map.empty
    )),
    failedFiles = Seq.empty
  ).toByteArray))
  when(pictureDao.create(any[Picture])(any[ExecutionContext]))
    .thenReturn(future(randomString()))
  when(pictureDao.createMany(any[Seq[Picture]])(any[ExecutionContext]))
    .thenReturn(future(Seq(randomString())))
  when(imagesCommonService.makeAlbumActive(any[String])).thenReturn(future(new RuntimeException("miss")))
  when(imagesCommonService.makeAlbumActive(eqTo(albumId))).thenReturn(future(Some(album)))

  "ImportFromS3ResultHandler#handle" should {
    "handle HandleJobResult message" in {
      handler ! HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(ImagesImportFromS3ResultHandler.Meta(
          albumId = albumId
        ))
      )

      expectMsgType[Unit]
    }
  }
}
