package baile.routes.onlinejob

import java.time.Instant

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import baile.daocommons.WithId
import baile.domain.asset.AssetScope
import baile.domain.onlinejob.{ OnlineJob, OnlineJobStatus, OnlinePredictionOptions }
import baile.domain.usermanagement.User
import baile.routes.RoutesSpec
import baile.routes.onlineJob.OnlineJobRoutes
import baile.services.common.AuthenticationService
import baile.services.onlinejob.{ OnlineJobService, OnlinePredictionCreateOptions }
import baile.services.usermanagement.util.TestData.SampleUser
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.when
import play.api.libs.json.{ JsBoolean, JsObject, JsString, Json }


class OnlineJobRoutesSpec extends RoutesSpec {

  val service: OnlineJobService = mock[OnlineJobService]
  val authenticationService = mock[AuthenticationService]
  val routes: Route = new OnlineJobRoutes(conf, authenticationService, service).routes
  implicit private val user: User = SampleUser
  private val modelId = "modelId"
  private val streamId = "streamId"
  private val bucketId = "bucketId"
  private val inputImagesPath = "inputImagesPath"
  private val outputAlbumId = "outputAlbumId"
  private val dateTime = Instant.now()
  private val onlineJob = WithId(OnlineJob(
    ownerId = user.id,
    name = "name",
    status = OnlineJobStatus.Running,
    options = OnlinePredictionOptions(
      streamId,
      modelId,
      bucketId,
      inputImagesPath,
      outputAlbumId
    ),
    enabled = true,
    created = dateTime,
    updated = dateTime,
    description = None
  ), "id")
  private val createRequest: String =
    """{
      |"name":"name",
      |"target":{"id":"id","type":"CV_MODEL"},
      |"enabled":true,
      |"options":{
      |"inputBucketId":"bucketId",
      |"inputImagesPath":"inputImagesPath",
      |"outputAlbumName":"outputAlbumId",
      |"type":"ONLINE_CV_PREDICTION"
      |}
      |}""".stripMargin

  when(authenticationService.authenticate(userToken)).thenReturn(future(Some(SampleUser)))

  "POST /online-jobs endpoint" should {

    "create onlineJob" in {
      when(service.create(
        eqTo(Some("name")),
        any[Boolean],
        any[OnlinePredictionCreateOptions],
        any[Option[String]]
      )(any[User])).thenReturn(future(Right(onlineJob)))

      Post("/online-jobs", Json.parse(createRequest)).signed.check {
        status shouldBe StatusCodes.OK
        validateOnlineJobResponse(responseAs[JsObject])
      }
    }

    "not be able to create onlineJob" in {
      when(service.create(
        any[Option[String]],
        any[Boolean],
        any[OnlinePredictionCreateOptions],
        any[Option[String]]
      )(any[User])).thenReturn(future(Left(OnlineJobService.OnlineJobServiceError.AccessDenied)))
      Post("/online-jobs", Json.parse(createRequest)).signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /online-jobs endpoint" should {

    "get onlineJobs list" in {
      when(service.list(
        any[Option[AssetScope]],
        any[Option[String]],
        any[List[String]],
        any[Int],
        any[Int],
        any[Option[String]],
        any[Option[String]]
      )(any[User])).thenReturn(future(Right((Seq(onlineJob), 1))))
      Get("/online-jobs").signed.check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JsObject]
        response.keys should contain allOf("data", "count")
        (response \ "count").as[Int] shouldBe 1
      }
    }

    "not be able to get onlineJobs list" in {
      when(service.list(
        any[Option[AssetScope]],
        any[Option[String]],
        any[List[String]],
        any[Int],
        any[Int],
        any[Option[String]],
        any[Option[String]]
      )(any[User])).thenReturn(future(Left(OnlineJobService.OnlineJobServiceError.AccessDenied)))
      Get("/online-jobs").signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /online-jobs/{id} endpoint" should {

    "get onlineJob" in {
      when(service.get(
        eqTo("id"),
        eqTo(Some("1234"))
      )(any[User]))
        .thenReturn(future(Right(onlineJob)))
      Get("/online-jobs/id?shared_resource_id=1234").signed.check {
        status shouldBe StatusCodes.OK
        validateOnlineJobResponse(responseAs[JsObject])
      }
    }

    "not be able to get onlineJob" in {
      when(service.get(
        any[String],
        eqTo(Some("1234"))
      )(any[User]))
        .thenReturn(future(Left(OnlineJobService.OnlineJobServiceError.OnlineJobNotFound)))
      Get("/online-jobs/n?shared_resource_id=1234").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "PUT /online-jobs/{id} endpoint" should {

    "update onlineJob name" in {
      when(service.update(
        eqTo("id"),
        any[Option[String]],
        any[Option[String]],
        any[Option[Boolean]]
      )(any[User]))
        .thenReturn(future(Right(onlineJob)))
      Put("/online-jobs/id", Json.parse("{\"name\": \"foo\",\"enabled\": " + true + "}")).signed.check {
        status shouldBe StatusCodes.OK
        validateOnlineJobResponse(responseAs[JsObject])
      }
    }

    "not be able to update onlineJob" in {
      when(service.update(
        any[String],
        any[Option[String]],
        any[Option[String]],
        any[Option[Boolean]]
      )(any[User]))
        .thenReturn(future(Left(OnlineJobService.OnlineJobServiceError.OnlineJobNotFound)))
      Put("/online-jobs/n", Json.parse("{\"name\": \"foo\",\"enabled\": " + true + "}")).signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "DELETE /online-jobs/{id} endpoint" should {

    "delete onlineJob" in {
      when(service.delete(
        eqTo("id")
      )(any[User]))
        .thenReturn(future(Right(())))
      Delete("/online-jobs/id").signed.check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JsObject]
        (response \ "id").as[String] shouldBe "id"
      }
    }

    "not be able to delete onlineJob" in {
      when(service.delete(
        any[String]
      )(any[User]))
        .thenReturn(future(Left(OnlineJobService.OnlineJobServiceError.OnlineJobNotFound)))
      Delete("/online-jobs/n").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  private def validateOnlineJobResponse(response: JsObject) = {
    response.fields should contain allOf(
      "id" -> JsString(onlineJob.id),
      "ownerId" -> JsString(onlineJob.entity.ownerId.toString),
      "name" -> JsString(onlineJob.entity.name),
      "enabled" -> JsBoolean(onlineJob.entity.enabled)
    )
    Instant.parse((response \ "created").as[String]) shouldBe onlineJob.entity.created
    Instant.parse((response \ "updated").as[String]) shouldBe onlineJob.entity.updated
    (response \ "target").as[JsObject].fields should contain allOf(
      "id" -> JsString(onlineJob.entity.options.target.id),
      "type" -> JsString("CV_MODEL")
    )
    (response \ "options").as[JsObject].fields should contain allOf(
      "inputBucketId" -> JsString("bucketId"),
      "inputImagesPath" -> JsString("inputImagesPath"),
      "outputAlbumId" -> JsString("outputAlbumId"),
      "type" -> JsString("ONLINE_CV_PREDICTION")
    )
  }

}
