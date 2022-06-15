package baile.routes.cv.model

import java.time.Instant

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import baile.domain.asset.AssetScope
import baile.domain.cv.model.{ CVModelStatus, CVModelType }
import baile.domain.usermanagement.User
import baile.routes.RoutesSpec
import baile.services.common.{ AuthenticationService, FileUploadService }
import baile.services.cv.model.{ CVModelRandomGenerator, CVModelService }
import baile.services.cv.model.CVModelService.CVModelServiceError
import baile.services.remotestorage.S3StorageService
import baile.services.usermanagement.util.TestData.SampleUser
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import play.api.libs.json._

class CVModelRoutesSpec extends RoutesSpec {

  private val authenticationService = mock[AuthenticationService]
  private val cvModelService = mock[CVModelService]
  private val s3StorageService = mock[S3StorageService]
  private val fileUploadService = new FileUploadService(s3StorageService, "prefix")

  val routes: Route = new CVModelRoutes(
    conf,
    authenticationService,
    cvModelService,
    fileUploadService,
    "http://localhost"
  ).routes

  implicit private val user: User = SampleUser
  val DateTime: Instant = Instant.now()

  private val model = CVModelRandomGenerator.randomModel(
    status = CVModelStatus.Training,
    modelType = CVModelType.TL(CVModelType.TLConsumer.Classifier("FCN_2"), "SCAE"),
    featureExtractorId = Some("feId"),
    description = None,
    ownerId = user.id
  )

  when(cvModelService.list(
    any[Option[AssetScope]],
    any[Option[String]],
    any[List[String]],
    any[Int],
    any[Int],
    any[Option[String]],
    any[Option[String]]
  )(any[User]))
    .thenReturn(future(Right((Seq(), 1))))
  when(cvModelService.count(
    any[Option[String]],
    any[Option[String]],
    any[Option[String]]
  )(any[User]))
    .thenReturn(future(Right(0)))
  when(cvModelService.get(
    any[String]
  )(any[User]))
    .thenReturn(future(Left(CVModelService.CVModelServiceError.ModelNotFound)))
  when(cvModelService.get(
    eqTo("m"),
  )(any[User]))
    .thenReturn(future(Right(model)))
  when(cvModelService.get(
    eqTo("1"),
    eqTo(Some("123"))
  )(any[User]))
    .thenReturn(future(Left(CVModelService.CVModelServiceError.ModelNotFound)))
  when(cvModelService.get(
    eqTo("1"),
    eqTo(Some("1234"))
  )(any[User]))
      .thenReturn(future(Right(model)))
  when(cvModelService.update(
    any[String],
    any[Option[String]],
    any[Option[String]]
  )(any[User]))
    .thenReturn(future(Left(CVModelService.CVModelServiceError.ModelNotFound)))
  when(cvModelService.update(
    eqTo("m"),
    any[Option[String]],
    any[Option[String]]
  )(any[User]))
    .thenReturn(future(Right(model)))
  when(cvModelService.delete(
    any[String]
  )(any[User]))
    .thenReturn(future(Left(CVModelService.CVModelServiceError.ModelNotFound)))
  when(cvModelService.delete(
    eqTo("m")
  )(any[User]))
    .thenReturn(future(Right(())))
  when(cvModelService.export(
    eqTo("m")
  )(any[User]))
    .thenReturn(future(Right(Source.empty)))
  when(cvModelService.getStateFileUrl(
    eqTo("m")
  )(any[User]))
    .thenReturn(future(Right("s3://m")))
  when(cvModelService.save(
    any[String],
    any[String],
    any[Option[String]]
  )(any[User]))
    .thenReturn(future(Left(CVModelService.CVModelServiceError.ModelNotFound)))
  when(cvModelService.save(
    eqTo("m"),
    any[String],
    any[Option[String]]
  )(any[User]))
    .thenReturn(future(Right(model)))
  when(authenticationService.authenticate(eqTo(userToken))).thenReturn(future(Some(SampleUser)))

  "GET /cv-models endpoint" should {
    "get models list" in {
      Get("/cv-models").signed.check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  "GET /cv-models/{id} endpoint" should {
    "get model" in {
      Get("/cv-models/1?shared_resource_id=1234").signed.check {
        status shouldBe StatusCodes.OK
      }
    }
    "get no model" in {
      Get("/cv-models/1?shared_resource_id=123").signed.check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }
  "GET /cv-models/{id}/export endpoint" should {
    "get uri for model file path" in {
      Get("/cv-models/m/export").signed.check {
        status shouldBe StatusCodes.OK
      }
    }
    "get no uri for model file path" in {
      when(cvModelService.export(
        eqTo("m")
      )(any[User])).thenReturn(future(Left(CVModelServiceError.ModelNotActive)))
      Get("/cv-models/m/export").signed.check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }
  "GET /cv-models/{id}/state-file-url endpoint" should {
    "get state file url for model" in {
      Get("/cv-models/m/state-file-url").signed.check {
        status shouldBe StatusCodes.OK
      }
    }
    "get no state file url for model" in {
      when(cvModelService.getStateFileUrl(
        eqTo("m")
      )(any[User])).thenReturn(future(Left(CVModelServiceError.ModelFilePathNotFound)))
      Get("/cv-models/m/state-file-url").signed.check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }
  "PUT /cv-models/{id} endpoint" should {
    "update model name" in {
      Put("/cv-models/m", Json.parse("{\"name\": \"foo\"}")).signed.check {
        status shouldBe StatusCodes.OK
      }
    }
    "fail on no model" in {
      Put("/cv-models/n", Json.parse("{\"name\": \"foo\"}")).signed.check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }
  "DELETE /cv-models/{id} endpoint" should {
    "delete model" in {
      Delete("/cv-models/m").signed.check {
        status shouldBe StatusCodes.OK
      }
    }
    "delete no model" in {
      Delete("/cv-models/n").signed.check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  "POST /cv-models/{id}/save endpoint" should {
    "save model with name and description" in {
      Post("/cv-models/m/save", Json.parse("{\"name\": \"foo\",\"description\": \"some description\"}")).signed.check {
        status shouldBe StatusCodes.OK
      }
    }
    "fail on no model" in {
      Post("/cv-models/n/save", Json.parse("{\"name\": \"foo\"}")).signed.check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }
}
