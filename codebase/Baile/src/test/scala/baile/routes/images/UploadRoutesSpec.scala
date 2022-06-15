package baile.routes.images

import java.time.Instant

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.util.ByteString
import baile.RandomGenerators._
import baile.daocommons.WithId
import baile.domain.images.{ Album, AlbumLabelMode, AlbumStatus, AlbumType }
import baile.routes.ExtendedRoutesSpec
import baile.services.common.FileUploadService
import baile.services.images.ImagesUploadService
import cats.implicits._
import play.api.libs.json._


class UploadRoutesSpec extends ExtendedRoutesSpec {

  trait Setup extends RoutesSetup { self =>
    val service = mock[ImagesUploadService]
    val fileUploadService = mock[FileUploadService]
    val albumId = randomString(16)

    val album = Album(
      ownerId = user.id,
      name = randomString(),
      status = AlbumStatus.Active,
      `type` = AlbumType.Source,
      labelMode = randomOf(AlbumLabelMode.Classification, AlbumLabelMode.Localization),
      created = Instant.now,
      updated = Instant.now,
      inLibrary = randomOf(true, false),
      picturesPrefix = randomString(),
      video = None,
      description = None,
      augmentationTimeSpentSummary = None
    )

    val csvData: ByteString = ByteString.fromString(
      """test,test
        |foo,bar
      """.stripMargin
    )

    val routes: Route = new UploadRoutes(service, fileUploadService).routes(albumId)

    def validateAlbumResponse(response: JsObject): Unit = {
      response.fields should contain allOf(
        "id" -> JsString(albumId),
        "ownerId" -> JsString(album.ownerId.toString),
        "name" -> JsString(album.name),
        "status" -> JsString(album.status.toString.toUpperCase),
        "type" -> JsString(album.`type`.toString.toUpperCase),
        "locked" -> JsBoolean(false),
        "labelMode" -> JsString(album.labelMode.toString.toUpperCase)
      )
      Instant.parse((response \ "created").as[String]) shouldBe album.created
      Instant.parse((response \ "updated").as[String]) shouldBe album.updated
      album.video.foreach { video =>
        (response \ "video").as[JsObject].fields should contain allOf(
          "filename" -> JsString(video.fileName),
          "filepath" -> JsString(video.filePath),
          "filesize" -> JsNumber(video.fileSize)
        )
      }
    }
  }

  "POST /albums/{id}/importPicturesFromS3 endpoint" should {
    "accept upload request with bucket ID" in new Setup {
      service.importImagesFromS3(*, *, *, *, *, *)(*) shouldReturn future(WithId(album, albumId).asRight)
      val uploadFormData =
        Multipart.FormData(
          Map(
            "AWSS3BucketId" -> HttpEntity(randomString()),
            "S3ImagesPath" -> HttpEntity(randomPath())
          )
        )

      Post("/importPicturesFromS3", uploadFormData).check {
        status shouldBe StatusCodes.Accepted
        validateAlbumResponse(responseAs[JsObject])
      }
    }

    "accept upload request with bucket access parameters" in new Setup {
      service.importImagesFromS3(*, *, *, *, *, *)(*) shouldReturn future(WithId(album, albumId).asRight)
      val uploadFormData =
        Multipart.FormData(
          Map(
            "AWSRegion" -> HttpEntity(randomOf("us-east-1", "us-east-2")),
            "AWSS3BucketName" -> HttpEntity(randomString()),
            "AWSAccessKey" -> HttpEntity(randomString()),
            "AWSSecretKey" -> HttpEntity(randomString()),
            "AWSSessionToken" -> HttpEntity(randomString()),
            "S3ImagesPath" -> HttpEntity(randomPath())
          )
        )

      Post("/importPicturesFromS3", uploadFormData).check {
        status shouldBe StatusCodes.Accepted
        validateAlbumResponse(responseAs[JsObject])
      }
    }

    "accept upload request with S3 CSV location" in new Setup {
      service.importImagesFromS3(*, *, *, *, *, *)(*) shouldReturn future(WithId(album, albumId).asRight)
      val uploadFormData =
        Multipart.FormData(
          Map(
            "AWSS3BucketId" -> HttpEntity(randomString()),
            "S3ImagesPath" -> HttpEntity(randomPath()),
            "S3CSVPath" -> HttpEntity(randomPath("csv"))
          )
        )

      Post("/importPicturesFromS3", uploadFormData).check {
        status shouldBe StatusCodes.Accepted
        validateAlbumResponse(responseAs[JsObject])
      }
    }

    "accept upload request with CSV uploaded" in new Setup {
      service.importImagesFromS3(*, *, *, *, *, *)(*) shouldReturn future(WithId(album, albumId).asRight)
      val uploadFormData =
        Multipart.FormData(
          Multipart.FormData.BodyPart.Strict("AWSS3BucketId", HttpEntity(randomString())),
          Multipart.FormData.BodyPart.Strict("S3ImagesPath", HttpEntity(randomPath())),
          Multipart.FormData.BodyPart.Strict(
            "file",
            HttpEntity(ContentTypes.`text/xml(UTF-8)`, csvData),
            Map("filename" -> "test.csv")
          )
        )

      Post("/importPicturesFromS3", uploadFormData).check {
        status shouldBe StatusCodes.Accepted
        validateAlbumResponse(responseAs[JsObject])
      }
    }

    "reject on incomplete request" in new Setup {
      val uploadFormData =
        Multipart.FormData(
          Map(
            "S3ImagesPath" -> HttpEntity(randomPath())
          )
        )

      Post("/importPicturesFromS3", uploadFormData).check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "accept upload request with applyLogTransformation flag" in new Setup {
      service.importImagesFromS3(*, *, *, *, *, *)(*) shouldReturn future(WithId(album, albumId).asRight)
      val uploadRequest = JsObject(Map(
        "AWSRegion" -> JsString(randomOf("us-east-1", "us-east-2")),
        "AWSS3BucketName" -> JsString(randomString()),
        "AWSAccessKey" -> JsString(randomString()),
        "AWSSecretKey" -> JsString(randomString()),
        "AWSSessionToken" -> JsString(randomString()),
        "S3ImagesPath" -> JsString(randomPath()),
        "applyLogTransformation" -> JsBoolean(randomBoolean())
      ))

      Post("/importPicturesFromS3", uploadRequest).check {
        status shouldBe StatusCodes.Accepted
        validateAlbumResponse(responseAs[JsObject])
      }
    }

    "reject upload request with empty AWS credentials" in new Setup {
      val uploadRequest = JsObject(Map(
        "AWSRegion" -> JsString(randomOf("us-east-1", "us-east-2")),
        "AWSS3BucketName" -> JsString(randomString()),
        "AWSAccessKey" -> JsString(""),
        "AWSSecretKey" -> JsString(""),
        "AWSSessionToken" -> JsString(randomString()),
        "S3ImagesPath" -> JsString(randomPath())
      ))

      Post("/importPicturesFromS3", uploadRequest).check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

  }

  "POST /albums/{id}/importVideoFromS3 endpoint" should {
    "accept upload request with bucket ID" in new Setup {
      service.importVideoFromS3(*, *, *, *)(*) shouldReturn future(WithId(album, albumId).asRight)
      val uploadRequest = JsObject(Map(
        "AWSS3BucketId" → JsString(randomString()),
        "S3VideoPath" → JsString(randomPath("mp4"))
      ))

      Post("/importVideoFromS3", uploadRequest).check {
        status shouldBe StatusCodes.OK
      }
    }

    "accept upload request with frame rate divider" in new Setup {
      service.importVideoFromS3(*, *, *, *)(*) shouldReturn future(WithId(album, albumId).asRight)
      val uploadRequest = JsObject(Map(
        "AWSS3BucketId" → JsString(randomString()),
        "S3VideoPath" → JsString(randomPath("mp4")),
        "frameRateDivider" → JsNumber(randomInt(1, 10))
      ))

      Post("/importVideoFromS3", uploadRequest).check {
        status shouldBe StatusCodes.OK
      }
    }

    "reject on incomplete request" in new Setup {
      val uploadRequest = JsObject(Map(
        "S3VideoPath" → JsString(randomPath("mp4"))
      ))

      Post("/importVideoFromS3", uploadRequest).check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  "POST /albums/{id}/importLabelsFromS3 endpoint" should {
    "accept upload request with bucket ID" in new Setup {
      service.importLabelsFromCSVFileS3(*, *, *)(*, *) shouldReturn future(WithId(album, albumId).asRight)
      val uploadRequest = JsObject(Map(
        "AWSS3BucketId" -> JsString(randomString()),
        "S3CSVPath" -> JsString(randomPath("csv"))
      ))

      Post("/importLabelsFromS3", uploadRequest).check {
        status shouldBe StatusCodes.OK
        validateAlbumResponse(responseAs[JsObject])
      }
    }

    "accept upload request with bucket access parameters" in new Setup {
      service.importLabelsFromCSVFileS3(*, *, *)(*, *) shouldReturn future(WithId(album, albumId).asRight)
      val uploadRequest = JsObject(Map(
        "AWSRegion" -> JsString(randomOf("us-east-1", "us-east-2")),
        "AWSS3BucketName" -> JsString(randomString()),
        "AWSAccessKey" -> JsString(randomString()),
        "AWSSecretKey" -> JsString(randomString()),
        "AWSSessionToken" -> JsString(randomString()),
        "S3CSVPath" -> JsString(randomPath("csv"))
      ))

      Post("/importLabelsFromS3", uploadRequest).check {
        status shouldBe StatusCodes.OK
        validateAlbumResponse(responseAs[JsObject])
      }
    }

    "accept upload request with bucket access parameters and AWSS3BucketId=null" in new Setup {
      service.importLabelsFromCSVFileS3(*, *, *)(*, *) shouldReturn future(WithId(album, albumId).asRight)
      val uploadRequest = JsObject(Map(
        "AWSS3BucketId" -> JsNull,
        "AWSRegion" -> JsString(randomOf("us-east-1", "us-east-2")),
        "AWSS3BucketName" -> JsString(randomString()),
        "AWSAccessKey" -> JsString(randomString()),
        "AWSSecretKey" -> JsString(randomString()),
        "AWSSessionToken" -> JsString(randomString()),
        "S3CSVPath" -> JsString(randomPath("csv"))
      ))

      Post("/importLabelsFromS3", uploadRequest).check {
        status shouldBe StatusCodes.OK
        validateAlbumResponse(responseAs[JsObject])
      }
    }

    "reject request with empty AWS credentials" in new Setup {
      val uploadRequest = JsObject(Map(
        "AWSRegion" -> JsString(randomOf("us-east-1", "us-east-2")),
        "AWSS3BucketName" -> JsString(randomString()),
        "AWSAccessKey" -> JsString(""),
        "AWSSecretKey" -> JsString(""),
        "AWSSessionToken" -> JsString(randomString()),
        "S3CSVPath" -> JsString(randomPath("csv"))
      ))
      Post("/importLabelsFromS3", uploadRequest).check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "reject on incomplete request" in new Setup {
      val uploadRequest = JsObject(Map(
        "S3CSVPath" -> JsString(randomPath("csv"))
      ))

      Post("/importLabelsFromS3", uploadRequest).check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /albums/{id}/uploadLabels endpoint" should {
    "accept upload request with file" in new Setup {
      service.importLabelsFromCSVFile(*, *)(*, *) shouldReturn future(WithId(album, albumId).asRight)
      val uploadFormData =
        Multipart.FormData(
          Multipart.FormData.BodyPart.Strict(
            "file",
            HttpEntity(ContentTypes.`text/xml(UTF-8)`, csvData),
            Map("filename" -> "test.csv")
          )
        )

      Post("/uploadLabels", uploadFormData).check {
        status shouldBe StatusCodes.OK
        validateAlbumResponse(responseAs[JsObject])
      }
    }

    "reject on incomplete request" in new Setup {
      val uploadFormData =
        Multipart.FormData()

      Post("/uploadLabels", uploadFormData).check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }
}
