package com.sentrana.umserver.controllers

import com.sentrana.umserver.entities.ApplicationInfoEntity
import com.sentrana.umserver.services.{ApplicationInfoQueryService, MongoDbService, ApplicationInfoService}
import com.sentrana.umserver.shared.dtos.ApplicationInfo
import com.sentrana.umserver.{IntegrationTestUtils, OneServerWithMongo, WithAdminUser}
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.libs.ws.{WS, WSResponse}
import play.api.test.Helpers._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by Alexander on 30.04.2016.
  */
class ApplicationInfoControllerSpec extends PlaySpec with OneServerWithMongo with WithAdminUser {
  import IntegrationTestUtils.IT_PREFIX
  import com.sentrana.umserver.entities.MongoFormats.applicationInfoEntityFormat

  private lazy val APPS_BASE_URL = s"$baseUrl/apps"

  private val AppInfo1_NAME = IT_PREFIX + "AppInfo1_NAME"

  private lazy val appInfoQueryService = app.injector.instanceOf(classOf[ApplicationInfoQueryService])
  private lazy val mongoDbService = app.injector.instanceOf(classOf[MongoDbService])
  private var applicationInfoId: String = _

  private implicit val applicationInfoReads = Json.reads[ApplicationInfo]

  "ApplicationInfoController" must {
    "return 404 when trying to get non-existent user" in {
      rootOrg
      val resp = await(WS.url(APPS_BASE_URL + "/noSuchApp").withQueryString("access_token" -> adminToken).get())
      withClue("Response body: " + resp.body) {
        resp.status mustBe (NOT_FOUND)
      }
    }

    "create new applicationInfo" in {
      val resp = await(createApplicationInfo(AppInfo1_NAME))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      applicationInfoId = (resp.json \ "id").as[String]
      applicationInfoId must not be empty
    }

    "read freshly created applicationInfo" in {
      val resp = await(WS.url(APPS_BASE_URL + "/" + applicationInfoId).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).get())
      withClue("Response body: " + resp.body) { resp.status mustBe(OK) }
      (resp.json \ "id").as[String] mustBe(applicationInfoId)
      (resp.json \ "name").as[String] mustBe(AppInfo1_NAME)
      (resp.json \ "desc").as[String] mustBe("fake description")
      (resp.json \ "url").as[String] mustBe("fake url")
      (resp.json \ "clientSecret").asOpt[String] mustBe None
      (resp.json \ "created").as[String] must not be empty
      (resp.json \ "updated").as[String] must not be empty
    }

    "not create applicationInfo with the name which is already exists" in {
      val resp = await(createApplicationInfo(AppInfo1_NAME))
      withClue("Response body: " + resp.body) { resp.status mustBe(BAD_REQUEST) }
    }

    "not update applicationInfo's name to the one which is already exists" in {
      val anotherApplicationInfo = itUtils.createTestApplicationInfo("AnotherAppInfo")
      val body = s"""
                    |{
                    |"name": "${AppInfo1_NAME}"
                    |}
        """.stripMargin
      val resp = await(updateApplicationInfo(anotherApplicationInfo.id, body))
      withClue("Response body: " + resp.body) { resp.status mustBe(BAD_REQUEST) }
    }

    "update some fields in applicationInfo" in {
      val body = s"""
                    |{
                    |"url": "fake url updated"
                    |}
        """.stripMargin
      val resp = await(updateApplicationInfo(applicationInfoId, body))
      withClue("Response body: " + resp.body) { resp.status mustBe(OK) }

      val applicationInfo = await(appInfoQueryService.get(applicationInfoId)).value
      verifyApplicationInfo(applicationInfo,
        applicationInfoId,
        AppInfo1_NAME,
        Some("fake description"),
        Some("fake url updated")
      )
    }

    "update all fields in applicationInfo" in {
      val body = s"""
                    |{"name": "${AppInfo1_NAME}_updated",
                    |"desc": "updated fake description",
                    |"url": "updated fake url"
                    |}
        """.stripMargin
      val resp = await(updateApplicationInfo(applicationInfoId, body))
      withClue("Response body: " + resp.body) { resp.status mustBe(OK) }

      val applicationInfo = await(appInfoQueryService.get(applicationInfoId)).value
      verifyApplicationInfo(applicationInfo,
        applicationInfoId,
        s"${AppInfo1_NAME}_updated",
        Some("updated fake description"),
        Some("updated fake url")
      )
    }

    "get clientSecret regenerate and get clientSecret once again" in {
      val oldClientSecretResp = await(WS.url(APPS_BASE_URL + "/" + applicationInfoId + "/clientsecret").withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).get())
      withClue("Response body: " + oldClientSecretResp.body) { oldClientSecretResp.status mustBe(OK) }
      val oldApplicationInfo = await(appInfoQueryService.get(applicationInfoId)).value
      val oldClientSecret = oldApplicationInfo.clientSecret
      verifyClientSecretInfoResponse(oldClientSecretResp, applicationInfoId, oldClientSecret)

      val regenerateSecretResp = await(WS.url(APPS_BASE_URL + "/" + applicationInfoId + "/clientsecret/regenerate").
        withQueryString("access_token" -> adminToken).
        post((Array.empty[Byte])))
      withClue("Response body: " + regenerateSecretResp.body) { regenerateSecretResp.status mustBe(OK) }

      val newClientSecretResp = await(WS.url(APPS_BASE_URL + "/" + applicationInfoId + "/clientsecret").withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).get())
      withClue("Response body: " + newClientSecretResp.body) { newClientSecretResp.status mustBe(OK) }
      val newApplicationInfo = await(appInfoQueryService.get(applicationInfoId)).value
      val newClientSecret = newApplicationInfo.clientSecret
      oldClientSecret must not be newClientSecret
      verifyClientSecretInfoResponse(newClientSecretResp, applicationInfoId, newClientSecret)
    }

    "regenerate clientSecret for unknown clientId" in {
      val regenerateSecretResp = await(WS.url(APPS_BASE_URL + "/" + "fake" + applicationInfoId + "/clientsecret/regenerate").
        withQueryString("access_token" -> adminToken).
        post((Array.empty[Byte])))
      withClue("Response body: " + regenerateSecretResp.body) { regenerateSecretResp.status mustBe(NOT_FOUND) }
    }

    "get clientSecret for unknown clientId" in {
      val clientSecretResp = await(WS.url(APPS_BASE_URL + "/" + "fake" + applicationInfoId + "/clientsecret").withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).get())
      withClue("Response body: " + clientSecretResp.body) { clientSecretResp.status mustBe(NOT_FOUND) }
    }

    "delete applicationInfo by id" in {
      await(WS.url(APPS_BASE_URL + "/" + applicationInfoId).withQueryString("access_token" -> adminToken).delete())
      await(appInfoQueryService.get(applicationInfoId)) mustBe empty
    }

    "create more applicationInfos" in {
      val additionalApplicationsAmount = 6
      val applicationInfos = await(createApplicationInfos(additionalApplicationsAmount))
      applicationInfos.size mustBe additionalApplicationsAmount
      applicationInfos.foreach(appInfoResp =>(appInfoResp.json \ "id").as[String] must not be empty)
    }

    "find applicationInfos with limit" in {
      val applicationInfosToReturnLimit = 4
      val resp = await(WS.url(APPS_BASE_URL).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "limit" -> s"${applicationInfosToReturnLimit}"
        ).get())

      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      val applicationInfos = (resp.json \ "data").as[Seq[ApplicationInfo]]
      applicationInfos.length mustBe applicationInfosToReturnLimit
    }

    "find applicationInfos with offset" in {
      val offset = 2
      val count = await(mongoDbService.count[ApplicationInfoEntity](Document()))

      val resp = await(WS.url(APPS_BASE_URL).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "offset" -> s"${offset}"
        ).get())

      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      val applicationInfos = (resp.json \ "data").as[Seq[ApplicationInfo]]
      applicationInfos.length mustBe (count - offset)
    }

    "find applicationInfos with limit and offset" in {
      val applicationInfoOffset = 2
      val applicationInfoLimit = 4

      val resp = await(WS.url(APPS_BASE_URL).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "offset" -> s"${applicationInfoOffset}",
          "limit" -> s"${applicationInfoLimit}"
        ).get())

      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      val applicationInfos = (resp.json \ "data").as[Seq[ApplicationInfo]]
      applicationInfos.length mustBe applicationInfoLimit
    }
  }

  private def createApplicationInfo(applicationName: String): Future[WSResponse] = {
    WS.url(APPS_BASE_URL).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).post(
      s"""
         |{"name": "$applicationName",
         |"desc": "fake description",
         |"url": "fake url"
         |}
        """.stripMargin
    )
  }

  private def updateApplicationInfo(appInfoId: String, body: String): Future[WSResponse] = {
    WS.url(APPS_BASE_URL + "/" + appInfoId).
      withHeaders(("Content-Type", "application/json")).
      withQueryString("access_token" -> adminToken).
      put(body)
  }

  private def createApplicationInfos(amount: Int): Future[Seq[WSResponse]] = {
    val applicationInfoWsResponses = (1 to amount).map { i =>
      createApplicationInfo(AppInfo1_NAME + i)
    }
    Future.sequence(applicationInfoWsResponses)
  }

  private def verifyApplicationInfo(applicationInfo: ApplicationInfoEntity,
                                    expectedApplicationInfoId: String,
                                    expectedName: String,
                                    expectedDesc: Option[String],
                                    expectedUrl: Option[String]) = {
    applicationInfo.id mustBe expectedApplicationInfoId
    applicationInfo.name mustBe expectedName
    applicationInfo.desc mustBe expectedDesc
    applicationInfo.url mustBe expectedUrl
    applicationInfo.clientSecret must not be empty
    Option(applicationInfo.created) mustBe defined
    Option(applicationInfo.updated) mustBe defined
  }

  private def verifyClientSecretInfoResponse(clientSecretInfoWsResponse: WSResponse,
                                             expectedApplicationInfoId: String,
                                            expectedClientSecret: String) = {
    (clientSecretInfoWsResponse.json \ "clientId").as[String] mustBe expectedApplicationInfoId
    (clientSecretInfoWsResponse.json \ "clientSecret").as[String] mustBe expectedClientSecret
  }
}
