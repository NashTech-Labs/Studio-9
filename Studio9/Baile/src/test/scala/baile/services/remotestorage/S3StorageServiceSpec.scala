package baile.services.remotestorage

import java.io.ByteArrayInputStream
import java.time.{ LocalDate, ZoneId }
import java.util.Date

import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.domain.common.S3Bucket.AccessOptions
import baile.services.common.S3BucketService
import baile.daocommons.sorting.Direction.Descending
import baile.daocommons.sorting.SortBy
import baile.services.remotestorage.sorting.LastModified
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.util.Success

class S3StorageServiceSpec extends ExtendedBaseSpec with TableDrivenPropertyChecks {

  trait Setup {

    val bucketName = "samplebucket"
    val accessOptions = AccessOptions(
      region = "us-east-1",
      bucketName = bucketName,
      accessKey = Some(randomString()),
      secretKey = Some(randomString()),
      sessionToken = Some(randomString())
    )
    val s3AccessPolicyPath = randomPath()
    val tempCredentialsDuration = randomInt(100)
    val s3CredentialsRoleArn = randomString()
    val s3ArnPartition = randomString()

    val s3BucketService = mock[S3BucketService]
    val s3Client = mock[AmazonS3Client]

    s3BucketService.prepareS3Client(accessOptions) shouldReturn Success(s3Client)

    val service = new S3StorageService(
      accessOptions,
      s3BucketService,
      s3AccessPolicyPath,
      tempCredentialsDuration,
      s3CredentialsRoleArn,
      s3ArnPartition
    )

    val objectMetadata = new ObjectMetadata
    val lastModified = new Date
    val objectSize = randomInt(20000)
    objectMetadata.setLastModified(lastModified)
    objectMetadata.setContentLength(objectSize)
  }

  "S3StorageService#write" should {

    val path = "path/to/save"
    val putObjectResult = new PutObjectResult
    putObjectResult.setMetadata(new ObjectMetadata)

    "save content to s3" in new Setup {
      val content = Array[Byte](1, 2, 3)
      s3Client.putObject(bucketName, path, *, *) shouldReturn putObjectResult

      whenReady(service.write(content, path)) { _ =>
        s3Client.putObject(bucketName, path, *, *) wasCalled once
      }
    }

  }

  "S3StorageService#doesExist" should {

    val path = "path/to/file"

    "return true when object exists" in new Setup {
      s3Client.doesObjectExist(bucketName, path) shouldReturn true

      whenReady(service.doesExist(path))(_ shouldBe true)
    }

    "return false when object does not exist" in new Setup {
      s3Client.doesObjectExist(bucketName, path) shouldReturn false

      whenReady(service.doesExist(path))(_ shouldBe false)
    }
  }

  "S3StorageService#readMeta" should {
    val path = "path/to/file"
    "return file when it exists" in new Setup {
      s3Client.getObjectMetadata(bucketName, path) shouldReturn objectMetadata

      whenReady(service.readMeta(path))(_ shouldBe File(path, objectSize, lastModified.toInstant))
    }
  }

  "S3StorageService#read" should {

    val path = "path/to/save"

    "successfully read content from s3" in new Setup {
      val content = Array[Byte](1, 2, 3)

      val s3Object = new S3Object
      s3Object.setObjectContent(new ByteArrayInputStream(content))

      s3Client.getObject(bucketName, path) shouldReturn s3Object

      whenReady(service.read(path)) { result =>
        result shouldBe content
      }
    }

    "fail to read content from s3 (object not found)" in new Setup {
      s3Client.getObject(bucketName, path) shouldThrow new AmazonServiceException(path)

      whenReady(service.read(path).failed) { result =>
        result shouldBe an [AmazonServiceException]
      }
    }

  }

  "S3StorageService#copy" should {
    "copy file from one location to another" in new Setup {
      val sourcePath = "path/to/file"
      val targetPath = "new/path/to/the/file"

      val copyObjectResult = new CopyObjectResult
      copyObjectResult.setLastModifiedDate(lastModified)

      val copyObjectMetadata = objectMetadata.clone()
      copyObjectMetadata.setLastModified(lastModified)

      s3Client.copyObject(bucketName, sourcePath, bucketName, targetPath) shouldReturn copyObjectResult
      s3Client.getObjectMetadata(bucketName, targetPath) shouldReturn copyObjectMetadata

      whenReady(service.copy(sourcePath, targetPath))(_ shouldBe File(targetPath, objectSize, lastModified.toInstant))
    }
  }

  "S3StorageService#move" should {
    "move file from one location to another" in new Setup {
      val sourcePath = "path/to/file"
      val targetPath = "new/path/to/the/file"

      val copyObjectResult = new CopyObjectResult
      copyObjectResult.setLastModifiedDate(lastModified)

      val copyObjectMetadata = objectMetadata.clone()
      copyObjectMetadata.setLastModified(lastModified)

      s3Client.copyObject(bucketName, sourcePath, bucketName, targetPath) shouldReturn copyObjectResult
      s3Client.getObjectMetadata(bucketName, targetPath) shouldReturn copyObjectMetadata

      whenReady(service.move(sourcePath, targetPath)) { file =>
        file shouldBe File(targetPath, objectSize, lastModified.toInstant)
        s3Client.copyObject(bucketName, sourcePath, bucketName, targetPath) wasCalled once
        s3Client.deleteObject(bucketName, sourcePath) wasCalled once
      }
    }
  }

  "S3StorageService#path" should {

    "join paths sections using /" in new Setup {
      service.path("foo", "bar", "baz") shouldBe "foo/bar/baz"
    }

    "strip / while merging sections" in new Setup {
      service.path("foo/", "/bar/", "baz") shouldBe "foo/bar/baz"
      service.path("/foo", "bar/", "baz/") shouldBe "foo/bar/baz"
    }

  }

  "S3StorageService#split" should {

    "split path into sections using /" in new Setup {
      forAll(
        Table(
          ("path", "segments"),
          ("foo/bar/baz", Seq("foo", "bar", "baz")),
          ("/foo/bar/baz", Seq("foo", "bar", "baz")),
          ("foo/bar/baz/", Seq("foo", "bar", "baz")),
          ("/foo/bar/baz/", Seq("foo", "bar", "baz")),
          ("foo", Seq("foo")),
          ("/foo/", Seq("foo"))
        )
      ) { (path, segments) =>
        service.split(path) shouldBe segments
      }
    }

  }

  "S3StorageService#delete" should {
    "delete file" in new Setup {
      val path = "path/to/delete"
      whenReady(service.delete(path)) { _ =>
        s3Client.deleteObject(bucketName, path) wasCalled once
      }
    }
  }

  "S3StorageService#createFolder" should {
    "create new folder" in new Setup {
      val path = "path/to/folder"
      s3Client.putObject(*, *, *[String]) shouldReturn new PutObjectResult
      whenReady(service.createDirectory(path)) { folder =>
        folder.path shouldBe path
        s3Client.putObject(*, *, *[String]) wasCalled once
      }
    }
  }

  "S3StorageService#doesDirectoryExist" should {

    val path = "path/to/folder"

    "return true when directory exists" in new Setup {
      s3Client.doesObjectExist(bucketName, *) shouldReturn true

      whenReady(service.doesDirectoryExist(path))(_ shouldBe true)
    }

    "return false when directory does not exist" in new Setup {
      s3Client.doesObjectExist(bucketName, *) shouldReturn false

      whenReady(service.doesExist(path))(_ shouldBe false)
    }

  }

  "S3StorageService#deleteDirectory" should {

    import scala.collection.JavaConverters._

    "delete directory and all objects inside it" in new Setup {
      val listing1 = mock[ObjectListing]
      val listing2 = mock[ObjectListing]
      s3Client.listObjects(*[ListObjectsRequest]) shouldReturn listing1
      listing1.getObjectSummaries shouldReturn seqAsJavaList(randomSummaries(5))
      listing1.isTruncated shouldReturn true
      s3Client.listNextBatchOfObjects(listing1) shouldReturn listing2
      listing2.getObjectSummaries shouldReturn seqAsJavaList(randomSummaries(5))
      listing2.isTruncated shouldReturn false

      whenReady(service.deleteDirectory("path/to/directory")) { _ =>
        s3Client.deleteObjects(*) wasCalled once
        s3Client.deleteObject(bucketName, *) wasCalled once
      }
    }

  }

  "S3StorageService#listDirectory" should {

    import scala.collection.JavaConverters._

    "return all objects inside directory" in new Setup {
      val listing1 = mock[ObjectListing]
      val listing2 = mock[ObjectListing]
      val summaries1 = randomSummaries(5)
      val summaries2 = randomSummaries(5)
      s3Client.listObjects(*[ListObjectsRequest]) shouldReturn listing1
      listing1.getObjectSummaries shouldReturn seqAsJavaList(summaries1)
      listing1.getCommonPrefixes shouldReturn seqAsJavaList(List("folder1/"))
      listing1.isTruncated shouldReturn true
      s3Client.listNextBatchOfObjects(listing1) shouldReturn listing2
      listing2.getObjectSummaries shouldReturn seqAsJavaList(summaries2)
      listing2.getCommonPrefixes shouldReturn seqAsJavaList(List("folder2/"))
      listing2.isTruncated shouldReturn false

      whenReady(service.listDirectory("path/to/directory", recursive = false)) { result =>
        val expectedFiles = (summaries1 ++ summaries2).map { summary =>
          File(summary.getKey, summary.getSize, summary.getLastModified.toInstant)
        }
        val expectedFolders = List(Directory("folder1"), Directory("folder2"))
        result should contain allElementsOf expectedFiles ++ expectedFolders
      }
    }

  }

  "S3StorageService#listFiles" should {

    import scala.collection.JavaConverters._

    "return all objects for specific path in accordance with page size and page number params" in new Setup {
      val numberOfFilesInSummary = 1
      val listing1 = mock[ObjectListing]
      val listing2 = mock[ObjectListing]
      val summaries1 = randomSummaries(numberOfFilesInSummary)
      val summaries2 = randomSummaries(numberOfFilesInSummary)
      s3Client.listObjects(any[ListObjectsRequest]) shouldReturn listing1
      listing1.getObjectSummaries shouldReturn seqAsJavaList(summaries1)
      listing1.getCommonPrefixes shouldReturn seqAsJavaList(List.empty)
      listing1.isTruncated shouldReturn true
      s3Client.listNextBatchOfObjects(listing1) shouldReturn listing2
      listing2.isTruncated shouldReturn false
      listing2.getObjectSummaries shouldReturn seqAsJavaList(summaries2)
      listing2.getCommonPrefixes shouldReturn seqAsJavaList(List.empty)

      whenReady(
        service.listFiles("path/to", 1, numberOfFilesInSummary, None, None, false
      )) { case (files, _) =>
        val expectedFiles = summaries1.map { summary =>
          File(summary.getKey, summary.getSize, summary.getLastModified.toInstant)
        }
        files should contain allElementsOf expectedFiles
      }

      whenReady(
        service.listFiles("path/to", 2, numberOfFilesInSummary, None, None, false
      )) { case (files, _) =>
        val expectedFiles = summaries2.map { summary =>
          File(summary.getKey, summary.getSize, summary.getLastModified.toInstant)
        }
        files should contain allElementsOf expectedFiles
      }
    }

    "return all objects for specific path sorted by modified date in descending order" in new Setup {
      val numberOfFilesInSummary = 1
      val listing1 = mock[ObjectListing]
      val listing2 = mock[ObjectListing]
      val summaries1 = randomSummaries(numberOfFilesInSummary)
      val summaries2 = randomSummaries(numberOfFilesInSummary, daysToAdd = 1)
      val sortBy = Some(SortBy(LastModified, Descending))
      s3Client.listObjects(any[ListObjectsRequest]) shouldReturn listing1
      listing1.getObjectSummaries shouldReturn seqAsJavaList(summaries1)
      listing1.isTruncated shouldReturn true
      s3Client.listNextBatchOfObjects(listing1) shouldReturn listing2
      listing2.getObjectSummaries shouldReturn seqAsJavaList(summaries2)
      listing2.isTruncated shouldReturn false

      whenReady(service.listFiles(
        "path/to", 1, numberOfFilesInSummary * 2, sortBy, None, true
      )) { case (files, _) =>
        val expectedFiles = (summaries2 ++ summaries1).map { summary =>
          File(summary.getKey, summary.getSize, summary.getLastModified.toInstant)
        }
        files should contain theSameElementsInOrderAs expectedFiles
      }
    }

    "return all objects for specific path filtered by param" in new Setup {
      val searchParam = "match"
      val numberOfFilesInSummary = 1
      val listing1 = mock[ObjectListing]
      val listing2 = mock[ObjectListing]
      val summaries1 = randomSummaries(numberOfFilesInSummary)
      summaries1.foreach(x => x.setKey(x.getKey() + searchParam))
      val summaries2 = randomSummaries(numberOfFilesInSummary, daysToAdd = 1)
      val sortBy = Some(SortBy(LastModified, Descending))
      s3Client.listObjects(any[ListObjectsRequest]) shouldReturn listing1
      listing1.getObjectSummaries shouldReturn seqAsJavaList(summaries1)
      listing1.getCommonPrefixes shouldReturn seqAsJavaList(List.empty)
      listing1.isTruncated shouldReturn true
      s3Client.listNextBatchOfObjects(listing1) shouldReturn listing2
      listing2.getObjectSummaries shouldReturn seqAsJavaList(summaries2)
      listing2.getCommonPrefixes shouldReturn seqAsJavaList(List.empty)
      listing2.isTruncated shouldReturn false

      whenReady(service.listFiles(
        "path/to", 1, numberOfFilesInSummary * 2, sortBy, Some(searchParam), false
      )) { case (files, _) =>
        val expectedFiles = (summaries1).map { summary =>
          File(summary.getKey, summary.getSize, summary.getLastModified.toInstant)
        }
        files should contain theSameElementsInOrderAs expectedFiles
      }
    }

  }

  private def randomSummaries(n: Int, daysToAdd: Int = 0): List[S3ObjectSummary] = List.fill(n) {
    val summary = new S3ObjectSummary
    summary.setKey(randomString())
    summary.setLastModified(
      Date.from(LocalDate.now().plusDays(daysToAdd).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant)
    )
    summary
  }
}
