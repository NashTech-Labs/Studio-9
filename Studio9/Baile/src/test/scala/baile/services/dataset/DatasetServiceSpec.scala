package baile.services.dataset

import java.time.Instant
import java.util.UUID
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.BaseSpec
import baile.dao.dataset.DatasetDao
import baile.daocommons.WithId
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Direction.Ascending
import baile.daocommons.sorting.SortBy
import baile.domain.asset.AssetType
import baile.domain.common.S3Bucket.{ AccessOptions, IdReference }
import baile.domain.dataset.{ Dataset, DatasetStatus }
import baile.domain.process.{ Process, ProcessStatus, ResultHandlerMeta }
import baile.domain.usermanagement.User
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.S3BucketService
import baile.services.cortex.job.CortexJobService
import baile.services.cortex.job.SupportedCortexJobTypes.SupportedCortexJobType
import baile.services.dataset.DatasetService.DatasetServiceError
import baile.services.dataset.DatasetService.DatasetServiceError.DatasetIsNotActive
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.services.remotestorage.{ File, RemoteStorageService, StreamedFile }
import baile.services.remotestorage.sorting.Filename
import baile.services.remotestorage.{ File, RemoteStorageService }
import baile.services.usermanagement.util.TestData
import baile.utils.streams.InputFileSource
import cats.implicits._
import com.typesafe.config.Config
import cortex.api.job.dataset.{ S3DatasetExportRequest, S3DatasetImportRequest }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.libs.json.{ JsObject, OWrites }

import scala.concurrent.ExecutionContext

class DatasetServiceSpec extends BaseSpec {

  trait Setup {
    val dao = mock[DatasetDao]
    val projectService = mock[ProjectService]
    val fileStorage = mock[RemoteStorageService]
    val assetSharingService = mock[AssetSharingService]
    val processService = mock[ProcessService]
    val conf = mock[Config]
    val s3BucketService = mock[S3BucketService]
    val cortexJobService = mock[CortexJobService]

    val service = new DatasetService(
      dao,
      projectService,
      fileStorage,
      "prefix",
      assetSharingService,
      s3BucketService,
      processService,
      cortexJobService
    )

    implicit val user: User = TestData.SampleUser

    val dataset = WithId(
      Dataset(
        ownerId = user.id,
        name = "name",
        status = DatasetStatus.Active,
        created = Instant.now(),
        updated = Instant.now(),
        description = Some("desc"),
        basePath = "basePath"
      ),
      "id"
    )
  }
  "DatasetService#create" should {

    "create dataset" in new Setup {
      when(dao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(0))
      when(dao.create(any[String => Dataset])(any[ExecutionContext])).thenReturn(future(dataset))

      whenReady(service.create(Some(dataset.entity.name), dataset.entity.description)) { result =>
        val createdDataset = result.right.get.entity
        createdDataset shouldBe dataset.entity.copy(
          created = createdDataset.created,
          updated = createdDataset.updated,
          basePath = createdDataset.basePath
        )
      }
    }

    "fail when name is empty" in new Setup {
      whenReady(service.create(Some(""), None))(_ shouldBe DatasetServiceError.EmptyDatasetName.asLeft)
    }

    "fail when name is taken" in new Setup {
      when(dao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(1))

      whenReady(service.create(Some("existingName"), None))(_ shouldBe DatasetServiceError.NameIsTaken.asLeft)
    }

  }

  "DatasetService#update" should {

    "update the dataset" in new Setup {
      when(dao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(0))
      when(dao.get(eqTo(dataset.id))(any[ExecutionContext])).thenReturn(future(Some(dataset)))
      when(dao.update(
        eqTo(dataset.id),
        any[Dataset => Dataset].apply
      )(any[ExecutionContext])).thenReturn(future(Some(dataset)))

      whenReady(service.update(dataset.id, Some(dataset.entity.name), None))(_ shouldBe dataset.asRight)
    }

  }

  "DatasetService#download" should {

    val file1Content = ByteString(randomString(200))
    val file2Content = ByteString(randomString(200))
    val file3Content = ByteString(randomString(200))

    val file1 = StreamedFile(
      file = File(path = "file1", size = 200l, lastModified = Instant.now),
      content = Source.single(file1Content)
    )
    val file2 = StreamedFile(
      file = File(path = "file2", size = 200l, lastModified = Instant.now),
      content = Source.single(file2Content)
    )
    val file3 = StreamedFile(
      file = File(path = "file3", size = 200l, lastModified = Instant.now),
      content = Source.single(file3Content)
    )

    "return source of all files in dataset" in new Setup {

      val expectedContent = List(file1, file2, file3)
      val expectedSource = Source(expectedContent)

      when(dao.get(eqTo(dataset.id))(any[ExecutionContext])).thenReturn(future(Some(dataset)))
      when(fileStorage.path(any[String], any[String])).thenReturn("path")
      when(fileStorage.streamFiles(eqTo("path"))(any[ExecutionContext])).thenReturn(future(expectedSource))

      whenReady(service.download(dataset.id, List.empty)) { result =>
        val resultSource = result.right.get
        val resultContent = resultSource.runFold(List.empty[StreamedFile])(_ :+ _).futureValue
        resultContent.map(_.file) shouldBe expectedContent.map(_.file)
        val resultFile1Content = resultContent(0).content
        val resultFile2Content = resultContent(1).content
        val resultFile3Content = resultContent(2).content

        resultFile1Content.runFold(ByteString.empty)(_ ++ _).futureValue shouldBe file1Content
        resultFile2Content.runFold(ByteString.empty)(_ ++ _).futureValue shouldBe file2Content
        resultFile3Content.runFold(ByteString.empty)(_ ++ _).futureValue shouldBe file3Content
      }
    }

    "return source of some files in dataset" in new Setup {

      val expectedContent = List(file1, file3)

      when(dao.get(eqTo(dataset.id))(any[ExecutionContext])).thenReturn(future(Some(dataset)))
      when(fileStorage.path(any[String], any[String])).thenReturn("path")
      when(fileStorage.streamFile(any[String])(any[ExecutionContext]))
        .thenReturn(future(file1))
        .thenReturn(future(file3))

      whenReady(service.download(dataset.id, List(file1.file.path, file3.file.path))) { result =>
        val resultSource = result.right.get
        val resultContent = resultSource.runFold(List.empty[StreamedFile])(_ :+ _).futureValue
        resultContent.map(_.file) shouldBe expectedContent.map(_.file)
        val resultFile1Content = resultContent(0).content
        val resultFile3Content = resultContent(1).content

        resultFile1Content.runFold(ByteString.empty)(_ ++ _).futureValue shouldBe file1Content
        resultFile3Content.runFold(ByteString.empty)(_ ++ _).futureValue shouldBe file3Content
      }

    }

  }

  "DatasetService#removeFile" should {
    val path = "path/to/file"

    "remove file" in new Setup {
      when(dao.get(any[String])(any[ExecutionContext])) thenReturn future(Some(dataset))
      when(fileStorage.path(any[String], any[String])) thenReturn path
      when(fileStorage.doesExist(any[String])(any[ExecutionContext])) thenReturn future(true)
      when(fileStorage.delete(any[String])(any[ExecutionContext])) thenReturn future(())
      whenReady(service.removeFile(dataset.id, path)) { result =>
        result shouldBe ().asRight
      }
    }

    "return error when dataset is not in active mode" in new Setup {
      val inactiveDataset: WithId[Dataset] = dataset.copy(
        entity = dataset.entity.copy(status = DatasetStatus.Exporting)
      )
      when(dao.get(dataset.id)) thenReturn future(Some(inactiveDataset))

      whenReady(service.removeFile(dataset.id, path)) { result =>
        result shouldBe DatasetServiceError.DatasetIsNotActive.asLeft
      }
    }

    "return error when file was not found" in new Setup {
      when(dao.get(any[String])(any[ExecutionContext])) thenReturn future(Some(dataset))
      when(fileStorage.path(any[String], any[String])) thenReturn path
      when(fileStorage.doesExist(any[String])(any[ExecutionContext])) thenReturn future(false)

      whenReady(service.removeFile(dataset.id, path))(_ shouldBe DatasetServiceError.FileNotFound.asLeft)
    }

    "DatasetService#importDatasetFromS3" should {

      val idReference = IdReference(randomString())
      val filePath = randomString()

      "successfully import dataset from s3" in new Setup {

        val jobId = UUID.randomUUID()
        val accessOptions = AccessOptions(
          region = randomString(),
          bucketName = randomString(),
          accessKey = Some(randomString()),
          secretKey = Some(randomString()),
          sessionToken = Some(randomString())
        )
        val processWithId = WithId(
          Process(
            targetId = randomString(),
            targetType = AssetType.CvModel,
            ownerId = user.id,
            authToken = None,
            jobId = jobId,
            status = ProcessStatus.Queued,
            progress = None,
            estimatedTimeRemaining = None,
            created = Instant.now(),
            started = None,
            completed = None,
            errorCauseMessage = None,
            errorDetails = None,
            onComplete = ResultHandlerMeta(
              handlerClassName = randomString(),
              meta = JsObject.empty
            ),
            auxiliaryOnComplete = Seq.empty
          ),
          randomString()
        )

        val expectedResult = WithId(dataset.entity.copy(status = DatasetStatus.Importing), dataset.id)
        when(dao.get(eqTo(dataset.id))(any[ExecutionContext])).thenReturn(future(Some(dataset)))
        when(dao.update(
          eqTo(dataset.id),
          any[Dataset => Dataset].apply
        )(any[ExecutionContext])).thenReturn(future(Some(expectedResult)))
        when(s3BucketService.dereferenceBucket(eqTo(idReference))).thenReturn(future(Right(accessOptions)))
        when(cortexJobService.submitJob(
          any[S3DatasetImportRequest],
          eqTo(user.id)
        )(eqTo(implicitly[SupportedCortexJobType[S3DatasetImportRequest]]))).thenReturn(future(jobId))
        when(processService.startProcess(
          eqTo(jobId),
          eqTo(dataset.id),
          eqTo(AssetType.Dataset),
          eqTo(classOf[DatasetImportFromS3ResultHandler]),
          any[DatasetImportFromS3ResultHandler.Meta],
          eqTo(user.id),
          any[Option[String]]
        )(eqTo(implicitly[OWrites[DatasetImportFromS3ResultHandler.Meta]]))).thenReturn(future(processWithId))
        whenReady(
          service.importDatasetFromS3(dataset.id, idReference, filePath)
        )(_ shouldBe expectedResult.asRight)
      }

      "return DatasetIsNotActive if it is being used" in new Setup {

        val datasetWithImport = WithId(dataset.entity.copy(status = DatasetStatus.Importing), dataset.id)
        when(dao.get(eqTo(dataset.id))(any[ExecutionContext])).thenReturn(future(Some(datasetWithImport)))
        whenReady(
          service.importDatasetFromS3(dataset.id, idReference, filePath)
        )(_ shouldBe DatasetIsNotActive.asLeft)
      }

    }

    "DatasetService#exportDatasetToS3" should {

      val idReference = IdReference(randomString())
      val filePath = randomString()

      "successfully export dataset To s3" in new Setup {

        val jobId = UUID.randomUUID()
        val accessOptions = AccessOptions(
          region = randomString(),
          bucketName = randomString(),
          accessKey = Some(randomString()),
          secretKey = Some(randomString()),
          sessionToken = Some(randomString())
        )
        val processWithId = WithId(
          Process(
            targetId = randomString(),
            targetType = AssetType.CvModel,
            ownerId = user.id,
            authToken = None,
            jobId = jobId,
            status = ProcessStatus.Queued,
            progress = None,
            estimatedTimeRemaining = None,
            created = Instant.now(),
            started = None,
            completed = None,
            errorCauseMessage = None,
            errorDetails = None,
            onComplete = ResultHandlerMeta(
              handlerClassName = randomString(),
              meta = JsObject.empty
            ),
            auxiliaryOnComplete = Seq.empty
          ),
          randomString()
        )

        val expectedResult = WithId(dataset.entity.copy(status = DatasetStatus.Exporting), dataset.id)
        when(dao.get(eqTo(dataset.id))(any[ExecutionContext])).thenReturn(future(Some(dataset)))
        when(dao.update(
          eqTo(dataset.id),
          any[Dataset => Dataset].apply
        )(any[ExecutionContext])).thenReturn(future(Some(expectedResult)))
        when(s3BucketService.dereferenceBucket(eqTo(idReference))).thenReturn(future(Right(accessOptions)))
        when(cortexJobService.submitJob(
          any[S3DatasetExportRequest],
          eqTo(user.id)
        )(eqTo(implicitly[SupportedCortexJobType[S3DatasetExportRequest]]))).thenReturn(future(jobId))
        when(processService.startProcess(
          eqTo(jobId),
          eqTo(dataset.id),
          eqTo(AssetType.Dataset),
          eqTo(classOf[DatasetExportToS3ResultHandler]),
          any[DatasetExportToS3ResultHandler.Meta],
          eqTo(user.id),
          any[Option[String]]
        )(eqTo(implicitly[OWrites[DatasetExportToS3ResultHandler.Meta]]))).thenReturn(future(processWithId))
        whenReady(
          service.exportDatasetToS3(dataset.id, idReference, filePath)
        )(_ shouldBe expectedResult.asRight)
      }

      "return DatasetIsNotActive if it is being used" in new Setup {

        val datasetWithExport = WithId(dataset.entity.copy(status = DatasetStatus.Exporting), dataset.id)
        when(dao.get(eqTo(dataset.id))(any[ExecutionContext])).thenReturn(future(Some(datasetWithExport)))
        whenReady(
          service.exportDatasetToS3(dataset.id, idReference, filePath)
        )(_ shouldBe DatasetIsNotActive.asLeft)
      }
    }

  }

  "DatasetService#listFiles" should {

    "list all files for a dataset" in new Setup {
      val file = File(
        path = randomPath(),
        size = randomInt(999),
        lastModified = Instant.now()
      )
      when(dao.get(eqTo(dataset.id))(any[ExecutionContext])).thenReturn(future(Some(dataset)))
      when(fileStorage.listFiles(
        any[String],
        any[Int],
        any[Int],
        eqTo(Some(SortBy(Filename, Ascending))),
        any[Option[String]],
        eqTo(true)
      )(any[ExecutionContext])).thenReturn(future((List(file), 1)))
      whenReady(
        service.listFiles(dataset.id, 1, 10, Seq("filename"), None, None)
      )(_ shouldBe (List(file), 1).asRight)
    }

    "return error when sorting field unknown" in new Setup {
      when(dao.get(eqTo(dataset.id))(any[ExecutionContext])).thenReturn(future(Some(dataset)))
      whenReady(
        service.listFiles(dataset.id, 1, 10, Seq("path"), None, None)
      )(_ shouldBe DatasetServiceError.SortingFieldUnknown.asLeft)

    }
  }

  "Dataset#uploadFile" should {

    "upload file " in new Setup {
      when(fileStorage.write(
        any[Source[ByteString, Any]],
        any[String]
      )(
        any[ExecutionContext],
        any[Materializer]
      )) thenReturn future(File("path", 1L, Instant.now))

      when(fileStorage.path(
        any[String],
        any[Seq[String]]: _*
      )) thenReturn "path"

      when(dao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(dataset)))

      val inputFileSource: Source[InputFileSource, Any] =
        Source.single(InputFileSource("file-name", Source.single(ByteString("1"))))
      whenReady(service.upload(randomString(), inputFileSource)) {
        _ shouldBe ().asRight
      }
    }

    "fail to upload file when no dataset found" in new Setup {
      when(dao.get(any[String])(any[ExecutionContext])).thenReturn(future(None))

      val inputFileSource: Source[InputFileSource, Any] =
        Source.single(InputFileSource("file-name", Source.single(ByteString("1"))))
      whenReady(service.upload(randomString(), inputFileSource)) {
        _ shouldBe DatasetServiceError.DatasetNotFound.asLeft
      }
    }

  }
}
