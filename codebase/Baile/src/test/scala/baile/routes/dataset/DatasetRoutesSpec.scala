package baile.routes.dataset

import java.time.Instant

import akka.http.scaladsl.model.ContentType.Binary
import akka.http.scaladsl.model.{ FormData, MediaTypes, Multipart, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.daocommons.WithId
import baile.domain.asset.AssetScope
import baile.domain.common.S3Bucket.AccessOptions
import baile.domain.dataset.{ Dataset, DatasetStatus }
import baile.domain.usermanagement.User
import baile.routes.RoutesSpec
import baile.services.common.{ AuthenticationService, FileUploadService }
import baile.services.dataset.DatasetService
import baile.services.dataset.DatasetService.DatasetServiceError
import baile.services.remotestorage.{ File, StreamedFile }
import baile.services.usermanagement.util.TestData.SampleUser
import baile.utils.streams.InputFileSource
import cats.implicits._
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.when
import play.api.libs.json.{ JsNumber, JsObject, JsString, Json }

class DatasetRoutesSpec extends RoutesSpec {

  val service: DatasetService = mock[DatasetService]
  val fileUploadingService: FileUploadService = mock[FileUploadService]
  val authenticationService: AuthenticationService = mock[AuthenticationService]
  val routes: Route = new DatasetRoutes(conf, authenticationService, fileUploadingService, service).routes
  implicit private val user: User = SampleUser
  private val dateTime = Instant.now()
  private val dataset = WithId(Dataset(
    ownerId = user.id,
    name = "name",
    status = DatasetStatus.Importing,
    description = None,
    created = dateTime,
    updated = dateTime,
    basePath = randomString()
  ), "id")

  private val createRequest: String =
    """{
      |"name":"name",
      |"description": "dataset"
      |}""".stripMargin

  val path = randomPath()
  val id = randomString()

  val s3Bucket = AccessOptions(
    region = randomString(),
    bucketName = randomString(),
    accessKey = Some(randomString()),
    secretKey = Some(randomString()),
    sessionToken = Some(randomString())
  )

  when(authenticationService.authenticate(eqTo(userToken))).thenReturn(future(Some(SampleUser)))

  "POST /datasets endpoint" should {

    "create dataset" in {
      when(service.create(
        eqTo(Some("name")),
        any[Option[String]]
      )(any[User])).thenReturn(future(Right(dataset)))

      Post("/datasets", Json.parse(createRequest)).signed.check {
        status shouldBe StatusCodes.OK
        validateDatasetResponse(responseAs[JsObject])
      }
    }

    "not be able to create dataset if name is taken" in {
      when(service.create(
        eqTo(Some("name")),
        any[Option[String]]
      )(any[User])).thenReturn(future(Left(DatasetServiceError.NameIsTaken)))
      Post("/datasets", Json.parse(createRequest)).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /datasets endpoint" should {

    "get datasets list" in {
      when(service.list(
        any[Option[AssetScope]],
        any[Option[String]],
        any[List[String]],
        any[Int],
        any[Int],
        any[Option[String]],
        any[Option[String]]
      )(any[User])).thenReturn(future(Right((Seq(dataset), 1))))
      Get("/datasets").signed.check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JsObject]
        response.keys should contain allOf("data", "count")
        (response \ "count").as[Int] shouldBe 1
      }
    }

    "not be able to get datasets list if sorting field is not known" in {
      when(service.list(
        any[Option[AssetScope]],
        any[Option[String]],
        any[List[String]],
        any[Int],
        any[Int],
        any[Option[String]],
        any[Option[String]]
      )(any[User])).thenReturn(future(Left(DatasetServiceError.SortingFieldUnknown)))
      Get("/datasets").signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /datasets/{id}/files" should {

    "upload multiple files" in {
      val firstFile = Multipart.FormData.BodyPart.Strict(
        "file", ByteString("1"), Map("filename" -> "test.png"), List()
      )
      val secondFile = Multipart.FormData.BodyPart.Strict(
        "file", ByteString("2"), Map("filename" -> "test2.png"), List()
      )
      val formData = Multipart.FormData(firstFile, secondFile)
      when(service.upload(
        any[String],
        any[Source[InputFileSource, Any]]
      )(any[User])).thenReturn(future(().asRight))
      Post("/datasets/id/files", formData).signed.check {
        status shouldEqual StatusCodes.OK
      }
    }

    "upload file" in {
      when(service.upload(
        any[String],
        any[Source[InputFileSource, Any]]
      )(any[User])).thenReturn(future(().asRight))
      val name = Multipart.FormData.BodyPart.Strict("fileName", "xyz")
      val formData = Multipart.FormData(name)
      Post("/datasets/id/files", formData).signed.check {
        status shouldEqual StatusCodes.OK
      }
    }

    "fail upload file when error" in {
      when(service.upload(
        any[String],
        any[Source[InputFileSource, Any]]
      )(any[User])).thenReturn(future(DatasetServiceError.DatasetNotFound.asLeft))
      val file = Multipart.FormData.BodyPart.Strict(
        "file", ByteString("1"), Map("filename" -> "test.png"), List()
      )
      val formData = Multipart.FormData(file)
      Post("/datasets/id/files", formData).signed.check {
        status shouldEqual StatusCodes.NotFound
      }
    }

  }

  "GET /datasets/{id} endpoint" should {

    "get dataset" in {
      when(service.get(
        eqTo("id"),
        any[Option[String]]
      )(any[User]))
        .thenReturn(future(Right(dataset)))
      Get("/datasets/id").signed.check {
        status shouldBe StatusCodes.OK
        validateDatasetResponse(responseAs[JsObject])
      }
    }

    "not be able to get dataset if dataset is not found" in {
      when(service.get(
        eqTo("id"),
        any[Option[String]]
      )(any[User]))
        .thenReturn(future(Left(DatasetServiceError.DatasetNotFound)))
      Get("/datasets/id").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "PUT /datasets/{id} endpoint" should {

    "update dataset name" in {
      when(service.update(
        eqTo("id"),
        eqTo(Some("newName")),
        any[Option[String]]
      )(any[User]))
        .thenReturn(future(Right(dataset)))
      Put("/datasets/id", Json.parse("{\"name\": \"newName\" }")).signed.check {
        status shouldBe StatusCodes.OK
        validateDatasetResponse(responseAs[JsObject])
      }
    }

    "not be able to update dataset if dataset is not found" in {
      when(service.update(
        any[String],
        any[Option[String]],
        any[Option[String]]
      )(any[User]))
        .thenReturn(future(Left(DatasetServiceError.DatasetNotFound)))
      Put("/datasets/n", Json.parse("{\"name\": \"foo\"}")).signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "DELETE /datasets/{id} endpoint" should {

    "delete dataset" in {
      when(service.delete(
        eqTo("id")
      )(any[User]))
        .thenReturn(future(Right(())))
      Delete("/datasets/id").signed.check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JsObject]
        (response \ "id").as[String] shouldBe "id"
      }
    }

    "not be able to delete dataset if dataset is not found" in {
      when(service.delete(
        any[String]
      )(any[User]))
        .thenReturn(future(Left(DatasetServiceError.DatasetNotFound)))
      Delete("/datasets/n").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "DELETE /datasets/{datasetId}/files/{datasetFileName}" should {

    "delete dataset file" in {
      when(service.removeFile(
        any[String],
        eqTo("fileName.txt")
      )(any[User])) thenReturn future(().asRight)
      Delete("/datasets/id/files/fileName.txt").signed.check {
        status shouldBe StatusCodes.OK
      }
    }

    "delete dataset file which name contains directory" in {
      when(service.removeFile(
        any[String],
        eqTo("path/to/fileName.txt")
      )(any[User])) thenReturn future(().asRight)
      Delete("/datasets/id/files/path/to/fileName.txt").signed.check {
        status shouldBe StatusCodes.OK
      }
    }

    "not be able to delete dataset file when file not found" in {
      when(service.removeFile(
        any[String],
        any[String]
      )(any[User])) thenReturn future(DatasetServiceError.FileNotFound.asLeft)
      Delete("/datasets/id/files/fileName.txt").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /datasets/{id}/import" should {

    "accept JSON request with bucket access parameters" in {
      val uploadRequest = JsObject(Map(
        "from" -> JsObject(Map(
          "s3Bucket" -> JsObject(Map(
            "AWSRegion" -> JsString(s3Bucket.region),
            "AWSS3BucketName" -> JsString(s3Bucket.bucketName),
            "AWSAccessKey" -> JsString(s3Bucket.accessKey.get),
            "AWSSecretKey" -> JsString(s3Bucket.secretKey.get),
            "AWSSessionToken" -> JsString(s3Bucket.sessionToken.get)
          )),
          "path" -> JsString(path)
        ))
      ))

      when(service.importDatasetFromS3(
        eqTo(id),
        eqTo(s3Bucket),
        eqTo(path)
      )(any[User]))
        .thenReturn(future(Right(dataset)))

      Post(s"/datasets/$id/import", uploadRequest).signed.check {
        status shouldBe StatusCodes.Accepted
        validateDatasetResponse(responseAs[JsObject])
      }
    }

    "not able to accept JSON request with bucket access parameters" in {

      val uploadRequest = JsObject(Map(
        "from" -> JsObject(Map(
          "s3Bucket" -> JsObject(Map(
            "AWSRegion" -> JsString(s3Bucket.region),
            "AWSS3BucketName" -> JsString(s3Bucket.bucketName),
            "AWSAccessKey" -> JsString(s3Bucket.accessKey.get),
            "AWSSecretKey" -> JsString(s3Bucket.secretKey.get),
            "AWSSessionToken" -> JsString(s3Bucket.sessionToken.get)
          )),
          "path" -> JsString(path)
        ))
      ))

      when(service.importDatasetFromS3(
        eqTo(id),
        eqTo(s3Bucket),
        eqTo(path)
      )(any[User]))
        .thenReturn(future(Left(DatasetServiceError.DatasetIsNotActive)))

      Post(s"/datasets/$id/import", uploadRequest).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /datasets/{id}/export" should {

    "accept JSON request with bucket access parameters" in {
      val uploadRequest = JsObject(Map(
        "to" -> JsObject(Map(
          "s3Bucket" -> JsObject(Map(
            "AWSRegion" -> JsString(s3Bucket.region),
            "AWSS3BucketName" -> JsString(s3Bucket.bucketName),
            "AWSAccessKey" -> JsString(s3Bucket.accessKey.get),
            "AWSSecretKey" -> JsString(s3Bucket.secretKey.get),
            "AWSSessionToken" -> JsString(s3Bucket.sessionToken.get)
          )),
          "path" -> JsString(path)
        ))
      ))

      when(service.exportDatasetToS3(
        eqTo(id),
        eqTo(s3Bucket),
        eqTo(path)
      )(any[User]))
        .thenReturn(future(Right(dataset)))

      Post(s"/datasets/$id/export", uploadRequest).signed.check {
        status shouldBe StatusCodes.Accepted
      }
    }

    "not able to accept JSON request with bucket access parameters" in {

      val uploadRequest = JsObject(Map(
        "to" -> JsObject(Map(
          "s3Bucket" -> JsObject(Map(
            "AWSRegion" -> JsString(s3Bucket.region),
            "AWSS3BucketName" -> JsString(s3Bucket.bucketName),
            "AWSAccessKey" -> JsString(s3Bucket.accessKey.get),
            "AWSSecretKey" -> JsString(s3Bucket.secretKey.get),
            "AWSSessionToken" -> JsString(s3Bucket.sessionToken.get)
          )),
          "path" -> JsString(path)
        ))
      ))

      when(service.exportDatasetToS3(
        eqTo(id),
        eqTo(s3Bucket),
        eqTo(path)
      )(any[User]))
        .thenReturn(future(Left(DatasetServiceError.DatasetIsNotActive)))

      Post(s"/datasets/$id/export", uploadRequest).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /datasets/{id}/download" should {

    "download the entire dataset in TAR archive" in {

      val filesSource = Source(List(
        StreamedFile(File("file1", 200l, Instant.now), Source.single(ByteString(randomString(200)))),
        StreamedFile(File("file2", 200l, Instant.now), Source.single(ByteString(randomString(200)))),
        StreamedFile(File("file3", 200l, Instant.now), Source.single(ByteString(randomString(200)))),
      ))

      when(service.download("id", List.empty, Some("1"))).thenReturn(future(filesSource.asRight))
      Get(s"/datasets/id/download?access_token=$userToken&shared_resource_id=1").check(Binary(MediaTypes.`application/x-tar`)) {
        status shouldBe StatusCodes.OK
        headers.map(_.name) should contain ("Content-Disposition")
      }
    }

  }

  "POST /datasets/{id}/download" should {

    "download some files from dataset in TAR archive" in {
      val formData = FormData("files" -> """["file1","file3"]""")

      val filesSource = Source(List(
        StreamedFile(File("file1", 200l, Instant.now), Source.single(ByteString(randomString(200)))),
        StreamedFile(File("file3", 200l, Instant.now), Source.single(ByteString(randomString(200)))),
      ))

      when(service.download("id", List("file1", "file3"), Some("1"))).thenReturn(future(filesSource.asRight))
      Post(s"/datasets/id/download?access_token=$userToken&shared_resource_id=1", formData).check(Binary(MediaTypes.`application/x-tar`)) {
        status shouldBe StatusCodes.OK
        headers.map(_.name) should contain("Content-Disposition")
      }

    }
  }

  "GET /datasets/{id}/ls" should {

    "successfully get list of files from a dataset" in {
      val url = randomString()
      val file = File(
        path = randomPath(),
        size = randomInt(999),
        lastModified = Instant.now()
      )
      val datasetListResponseData = Json.obj(
        "count" -> JsNumber(1),
        "data" -> Seq(Json.obj(
          "filename" -> JsString(file.path),
          "filepath" -> JsString(url),
          "filesize" -> JsNumber(file.size),
          "modified" -> JsString(file.lastModified.toString)
        ))
      )
      when(service.listFiles(
        eqTo(id),
        any[Int],
        any[Int],
        any[Seq[String]],
        any[Option[String]],
        any[Option[String]]
      )(any[User]))
        .thenReturn(future(Right((List(file), 1))))
      when(service.signFiles(eqTo(id), eqTo(Seq(file)), any[Option[String]])(any[User]))
        .thenReturn(future(Right(List(url))))

      Get(s"/datasets/$id/ls").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe datasetListResponseData
      }
    }

    "return error in case of invalid sorting field" in {
      when(service.listFiles(
        eqTo(id),
        any[Int],
        any[Int],
        any[Seq[String]],
        any[Option[String]],
        any[Option[String]]
      )(any[User]))
        .thenReturn(future(Left(DatasetServiceError.SortingFieldUnknown)))

      Get(s"/datasets/$id/ls").signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  private def validateDatasetResponse(response: JsObject) = {
    response.fields should contain allOf(
      "id" -> JsString(dataset.id),
      "ownerId" -> JsString(dataset.entity.ownerId.toString),
      "name" -> JsString(dataset.entity.name)
    )
    Instant.parse((response \ "created").as[String]) shouldBe dataset.entity.created
    Instant.parse((response \ "updated").as[String]) shouldBe dataset.entity.updated
  }

}
