package baile.routes.asset.sharing

import java.time.Instant

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import baile.routes.RoutesSpec
import baile.routes.asset.sharing.util.TestData
import baile.services.asset.sharing.AssetSharingService
import baile.services.asset.sharing.AssetSharingService.AssetSharingServiceError
import baile.services.common.AuthenticationService
import baile.services.usermanagement.util.TestData.SampleUser
import org.mockito.Mockito.when
import play.api.libs.json._

class AssetSharingRoutesSpec extends RoutesSpec {

  val mockedAssertSharingService: AssetSharingService = mock[AssetSharingService]
  val authenticationService: AuthenticationService = mock[AuthenticationService]
  val routes: Route = new AssetSharingRoutes(conf, authenticationService, mockedAssertSharingService).routes
  implicit val user = SampleUser

  when(authenticationService.authenticate(userToken)).thenReturn(future(Some(SampleUser)))

  "POST /shares endpoint" should {

    "return success response from createSharedResource" in {
      when(mockedAssertSharingService.create(
        TestData.SharedResourceRequestEntity.name,
        TestData.SharedResourceRequestEntity.recipientId,
        TestData.SharedResourceRequestEntity.recipientEmail,
        TestData.SharedResourceRequestEntity.assetType,
        TestData.SharedResourceRequestEntity.assetId
      )) thenReturn future(Right(TestData.SharedResourceWithId))

      Post("/shares", Json.parse(TestData.SharedResourceRequestAsJson)).signed.check {
        status shouldEqual StatusCodes.OK
        validateSharedResourceResponse(responseAs[JsObject])
      }
    }
  }

  "GET /shares endpoint" should {

    "return success response from get SharedResources" in {
      when(mockedAssertSharingService.list(
        Some(TestData.AssetId),
        Some(TestData.AssetType)
      )) thenReturn future(Right((Seq(TestData.SharedResourceWithId), 1)))

      Get("/shares?asset_id=" + TestData.AssetId + "&asset_type=TABLE").signed.check {
        status shouldEqual StatusCodes.OK
        val response = responseAs[JsObject]
        response.keys should contain allOf("data", "count")
        (response \ "count").as[Int] shouldBe 1
      }
    }


    "return proper error response from get SharedResources when error comes in getSharedResources" in {
      when(mockedAssertSharingService.list(
        Some(TestData.AssetId),
        Some(TestData.AssetType)
      )) thenReturn future(Left(AssetSharingServiceError.RecipientNotFound))


      Get("/shares?asset_id=" + TestData.AssetId + "&asset_type=TABLE&page=1&page_size=1").signed.check {
        status shouldEqual StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "GET /me/shares endpoint" should {

      "return success response" in {
        when(mockedAssertSharingService.listAll(user.id)) thenReturn
          future(Seq(TestData.SharedResourceWithId))

        Get("/me/shares").signed.check {
          status shouldEqual StatusCodes.OK
          val response = responseAs[JsObject]
          response.keys should contain allOf("data", "count")
          (response \ "count").as[Int] shouldBe 1
        }
      }
    }

    "GET /shares/<Id> endpoint" should {

      "return success response from getSharedResourceById" in {
        when(mockedAssertSharingService.get(TestData.Id)) thenReturn
          future(Right(TestData.SharedResourceWithId))

        Get("/shares/" + TestData.Id).signed.check {
          status shouldEqual StatusCodes.OK
          validateSharedResourceResponse(responseAs[JsObject])
        }
      }

      "return error response from getSharedResourceById" in {
        when(mockedAssertSharingService.get(TestData.Id)) thenReturn
          future(Left(AssetSharingServiceError.ResourceNotFound))

        Get("/shares/" + TestData.Id).signed.check {
          status shouldEqual StatusCodes.NotFound
          validateErrorResponse(responseAs[JsObject])
        }
      }
    }

    "DELETE /shares/<Id> endpoint" should {

      "return success response from deleteSharedResource" in {
        when(mockedAssertSharingService.delete(TestData.Id)) thenReturn future(Right(()))

        Delete("/shares/" + TestData.Id).signed.check {
          status shouldEqual StatusCodes.OK
          responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("id")))
        }
      }

      "return error response from deleteSharedResource" in {
        when(mockedAssertSharingService.delete(TestData.Id)) thenReturn
          future(Left(AssetSharingServiceError.AccessDenied))

        Delete("/shares/" + TestData.Id).signed.check {
          status shouldEqual StatusCodes.Forbidden
          validateErrorResponse(responseAs[JsObject])
        }
      }

    }

    "GET /shares/<Id>/owner endpoint" should {

      "return success response" in {
        when(mockedAssertSharingService.getOwner(TestData.Id)) thenReturn future(Right(user))

        Get("/shares/" + TestData.Id + "/owner").signed.check {
          status shouldEqual StatusCodes.OK
          responseAs[JsObject] shouldBe TestData.UserResponseData
        }
      }

      "return error response" in {
        when(mockedAssertSharingService.getOwner(TestData.Id)) thenReturn
          future(Left(AssetSharingServiceError.AccessDenied))

        Get("/shares/" + TestData.Id + "/owner").signed.check {
          status shouldEqual StatusCodes.Forbidden
          validateErrorResponse(responseAs[JsObject])
        }
      }
    }

    "GET /shares/<Id>/recipient endpoint" should {

      "return success response" in {
        when(mockedAssertSharingService.getRecipient(TestData.Id)) thenReturn future(Right(user))

        Get("/shares/" + TestData.Id + "/recipient").signed.check {
          status shouldEqual StatusCodes.OK
          responseAs[JsObject] shouldBe TestData.UserResponseData
        }
      }

      "return error response" in {
        when(mockedAssertSharingService.getRecipient(TestData.Id)) thenReturn
          future(Left(AssetSharingServiceError.AccessDenied))

        Get("/shares/" + TestData.Id + "/recipient").signed.check {
          status shouldEqual StatusCodes.Forbidden
          validateErrorResponse(responseAs[JsObject])
        }
      }
    }
  }


  private def validateSharedResourceResponse(response: JsObject): Unit = {
    response.fields should contain allOf(
      "id" -> JsString(TestData.Id),
      "ownerId" -> JsString(TestData.SharedResourceEntity.ownerId.toString),
      "name" -> JsString(TestData.SharedResourceEntity.name.get),
      "recipientId" -> JsString(TestData.SharedResourceEntity.recipientId.get.toString),
      "recipientEmail" -> JsString(TestData.SharedResourceEntity.recipientEmail.get),
      "assetType" -> JsString("TABLE"),
      "assetId" -> JsString(TestData.SharedResourceEntity.assetId)
    )
    Instant.parse((response \ "created").as[String]) shouldBe TestData.SharedResourceEntity.created
    Instant.parse((response \ "updated").as[String]) shouldBe TestData.SharedResourceEntity.updated
  }

}
