package baile.services.common

import java.time.Instant

import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import baile.BaseSpec
import baile.domain.usermanagement.User
import baile.services.common.MLEntityExportImportService.EntityFileSavedResult
import baile.services.common.MLEntityExportImportService.EntityImportError.{ ImportHandlingFailed, InvalidMetaFormat, MetaIsTooBig }
import baile.services.cortex.job.CortexJobService
import baile.services.remotestorage.{ File, RemoteStorageService, StreamedFile }
import baile.services.usermanagement.util.TestData.SampleUser
import cats.data.EitherT
import cats.implicits._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._

import scala.concurrent.{ ExecutionContext, Future }

class MLEntityExportImportServiceSpec extends BaseSpec {

  private val cortexJobService = mock[CortexJobService]
  private val remoteStorage = mock[RemoteStorageService]
  private val maxEntityFileSize = 1024 * 50 // 50 KB
  private val service = new MLEntityExportImportService(cortexJobService, remoteStorage, maxEntityFileSize)

  implicit private val user: User = SampleUser
  private val sampleMeta = "meta"

  object Import {
    val SampleSource = Source(List(ByteString(
      s"""{"metaVersion":"1.0","entityMeta":"$sampleMeta"}""" + '\u0000' + randomString(100)
    )))

    val HugeMeta = randomString(maxEntityFileSize + 1)
    val HugeSource = Source(List(ByteString(HugeMeta)))

    val InvalidMeta = randomString()
    val InvalidSource = Source(List(ByteString(InvalidMeta + '\u0000' + randomString(100))))

    when(remoteStorage.path(any[String], any[String])).thenReturn("")
    when(remoteStorage.getSink(any[String])(any[ExecutionContext])).thenReturn(
      Sink.ignore.mapMaterializedValue(_.map(_ => File(randomString(), randomInt(2000), Instant.now)))
    )
  }

  object Export {
    val EntityFilePath = "path/to/file"
    val EntityFileContent = randomString(100)
    val SampleSource = Source(List(ByteString(EntityFileContent)))
    val SampleFile = StreamedFile(File(EntityFilePath, 100l, Instant.now), SampleSource)

    when(remoteStorage.streamFile(EntityFilePath)).thenReturn(future(SampleFile))
  }

  "MlEntityExportImportService#importEntity" should {

    import Import._

    "successfully import entity" in {

      def myHandler(entityFileSavedResult: EntityFileSavedResult[String]): Future[Either[String, Int]] = {
        entityFileSavedResult.meta shouldBe sampleMeta
        future(42.asRight)
      }

      whenReady(
        service.importEntity[String, Int, String](
          SampleSource,
          _ => EitherT.rightT[Future, String](()),
          myHandler
        )
      )(_ shouldBe 42.asRight)
    }

    "return meta is too big error" in {
      whenReady(
        service.importEntity[String, Int, String](
          HugeSource,
          _ => EitherT.rightT[Future, String](()),
          _ => future(42.asRight)
        )
      ) { result =>
        result shouldBe MetaIsTooBig.asLeft
      }
    }

    "return meta is bad format error" in {
      whenReady(
        service.importEntity[String, Int, String](
          InvalidSource,
          _ => EitherT.rightT[Future, String](()),
          _ => future(42.asRight)
        )
      ) { result =>
        result should matchPattern { case Left(InvalidMetaFormat(_)) => }
      }
    }

    "return client's custom error" in {

      def myHandler(entityFileSavedResult: EntityFileSavedResult[String]): Future[Either[String, Int]] = {
        entityFileSavedResult.meta shouldBe sampleMeta
        future("error".asLeft)
      }

      whenReady(
        service.importEntity[String, Int, String](
          SampleSource,
          _ => EitherT.rightT[Future, String](()),
          myHandler
        )
      )(_ shouldBe ImportHandlingFailed("error").asLeft)
    }

  }

  "MlEntityExportImportService#exportEntity" should {

    import Export._

    "successfully export entity" in {
      whenReady {
        for {
          exportedSource <- service.exportEntity(EntityFilePath, sampleMeta)
          result <- exportedSource.runReduce(_ ++ _)
        } yield result
      } { result =>
        result.utf8String shouldBe
          s"""{"metaVersion":"1.0","entityMeta":"$sampleMeta"}""" + '\u0000' + EntityFileContent
      }
    }

  }


}
