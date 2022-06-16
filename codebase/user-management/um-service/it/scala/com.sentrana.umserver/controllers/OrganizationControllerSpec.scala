package com.sentrana.umserver.controllers

import com.sentrana.umserver.services.{OrganizationQueryService, MongoDbService, OrganizationService}
import com.sentrana.umserver.shared.JsonFormatsShared
import com.sentrana.umserver.shared.dtos.{DataFilterInstance, Organization}
import com.sentrana.umserver.shared.dtos.enums.OrganizationStatus
import com.sentrana.umserver.{IntegrationTestUtils, OneServerWithMongo, WithAdminUser}
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.libs.ws.{WS, WSResponse}
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by Paul Lysak on 11.04.16.
  */
class OrganizationControllerSpec extends PlaySpec with OneServerWithMongo with WithAdminUser {

  import com.sentrana.umserver.JsonFormats.organizationFormat
  import com.sentrana.umserver.entities.MongoFormats.organizationMongoFormat

  private implicit lazy val filterOperatorReads = new JsonFormatsShared().filterOperatorReads
  private implicit val dataFilterInstanceReads = Json.reads[DataFilterInstance]

  private lazy val ORGS_BASE_URL = s"$baseUrl/orgs"

  private lazy val orgsQueryService = app.injector.instanceOf(classOf[OrganizationQueryService])
  private lazy val mongoDbService = app.injector.instanceOf(classOf[MongoDbService])

  private lazy val rootOrgId = rootOrg.id
  private val organizationName = "sampleOrg1"
  private var org1Id: String = _

  private lazy val testOrg1 = itUtils.createTestOrg("testOrg1", rootOrgId)
  private lazy val testOrg2 = itUtils.createTestOrg("testOrg2", rootOrgId)

  private lazy val o1testUser1 = itUtils.createTestUser("o1TestUser1", orgId = testOrg1.id, groupIds = Set(adminGroup.id))
  private lazy val o1testUserToken = authService.issueToken(o1testUser1)._1
  private lazy val o2testUser1 = itUtils.createTestUser("o2TestUser1", orgId = testOrg2.id, groupIds = Set(adminGroup.id))
  private lazy val o2testUserToken = authService.issueToken(o2testUser1)._1

  "OrganizationController" must {
    "return 404 when trying to get non-existent organization" in {
      rootOrgId
      val resp = await(WS.url(ORGS_BASE_URL + "/noSuchOrg").withQueryString("access_token" -> adminToken).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(NOT_FOUND) }
    }

    "not create another root organization" in {
      val resp = await(WS.url(ORGS_BASE_URL).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).post(
        s"""
          |{"name": "sampleOrg1",
          |"desc": "First org"
          |}
        """.stripMargin
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
    }

    "not create organization with too long name" in {
      val longOrgName = "sampleOrg1"*500
      val resp = await(WS.url(ORGS_BASE_URL).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).post(
        s"""
          |{"name": "$longOrgName",
          |"desc": "First org",
          |"parentOrganizationId": "$rootOrgId"
          |}
        """.stripMargin
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(REQUEST_ENTITY_TOO_LARGE) }
    }

    "create new organization" in {
      val resp = await(createOrganization(organizationName))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      org1Id = (resp.json \ "id").as[String]
      org1Id must not be empty

      val actualOrg = await(orgsQueryService.getMandatory(org1Id))
      actualOrg.id must be (org1Id)
      actualOrg.name must be (organizationName)
      actualOrg.desc.value must be ("First org")
      actualOrg.applicationIds must be (empty)
      actualOrg.dataFilterInstances must be (empty)
    }

    "get root organization details by id" in {
      val resp = await(WS.url(ORGS_BASE_URL + "/" + rootOrgId).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      (resp.json \ "id").as[String] must be (rootOrgId)
      (resp.json \ "name").as[String] must be (rootOrg.name)
      (resp.json \ "desc").asOpt[String] must be (empty)
      (resp.json \ "parentOrganizationId").asOpt[String] must be (empty)
      (resp.json \ "status").as[String] must be (OrganizationStatus.ACTIVE.toString)
      (resp.json \ "applicationIds").as[Seq[String]] must be (empty)
      (resp.json \ "dataFilterInstances").as[Seq[DataFilterInstance]] must be (empty)
    }

    "get root organization details by special url" in {
      val resp = await(WS.url(ORGS_BASE_URL + "/root").withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
      (resp.json \ "id").as[String] must be (rootOrgId)
      (resp.json \ "name").as[String] must be (rootOrg.name)
    }

    "get organization details" in {
      val resp = await(WS.url(ORGS_BASE_URL + "/" + org1Id).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      (resp.json \ "id").as[String] must be (org1Id)
      (resp.json \ "name").as[String] must be ("sampleOrg1")
      (resp.json \ "desc").as[String] must be ("First org")
      (resp.json \ "parentOrganizationId").as[String] must be (rootOrgId)
      (resp.json \ "status").as[String] must be (OrganizationStatus.ACTIVE.toString)
      (resp.json \ "applicationIds").as[Seq[String]] must be (empty)
    }

    "update organization" in {
      val body = s"""
                    |{"name": "org1",
                    |"desc": "First organization",
                    |"applicationIds": ["111", "222"]
                    |}
        """.stripMargin
      val resp = await(updateOrganization(org1Id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      org1Id = (resp.json \ "id").as[String]
      org1Id must not be empty

      val actualOrg = await(orgsQueryService.getMandatory(org1Id))
      actualOrg.id must be (org1Id)
      actualOrg.name must be ("org1")
      actualOrg.desc.value must be ("First organization")
      actualOrg.applicationIds must be (Set("111", "222"))
    }

    "not delete root organization" in {
      val resp = await(WS.url(ORGS_BASE_URL + "/" + rootOrgId).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).delete())
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
    }

    "delete organization" in {
      val resp = await(WS.url(ORGS_BASE_URL + "/" + org1Id).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).delete())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val actualOrg = await(orgsQueryService.get(org1Id)).value
      actualOrg.status must be (OrganizationStatus.DELETED)
    }

    "create additional organizations" in {
      val additionalOgranizationsAmount = 6
      val ogranizations = await(createOrganizations(additionalOgranizationsAmount))
      ogranizations.size mustBe additionalOgranizationsAmount
      ogranizations.foreach(orgResp =>(orgResp.json \ "id").as[String] must not be empty)
    }

    "find organizations with limit" in {
      val organizationsToReturnLimit = 4
      val resp = await(WS.url(ORGS_BASE_URL).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "limit" -> s"${organizationsToReturnLimit}"
        ).get())

      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      val organizations = (resp.json \ "data").as[Seq[Organization]]
      organizations.length mustBe organizationsToReturnLimit
    }

    "find organizations with offset" in {
      val offset = 2
      val count = await(mongoDbService.count[Organization](Document()))

      val resp = await(WS.url(ORGS_BASE_URL).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "offset" -> s"${offset}"
        ).get())

      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      val organizations = (resp.json \ "data").as[Seq[Organization]]
      organizations.length mustBe (count - offset)
    }

    "find organizations with limit and offset" in {
      val organizationsOffset = 2
      val organizationsLimit = 4

      val resp = await(WS.url(ORGS_BASE_URL).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "offset" -> s"${organizationsOffset}",
          "limit" -> s"${organizationsLimit}"
        ).get())

      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      val organizations = (resp.json \ "data").as[Seq[Organization]]
      organizations.length mustBe organizationsLimit
    }

    "not update root org by regular org admin" in {
      val body = s"""
                    |{
                    |"desc": "Updated descr"
                    |}
        """.stripMargin
      val resp = await(updateOrganization(rootOrgId, body, o1testUserToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(FORBIDDEN) }
    }

    "not update regular org by its admin" in {
      val body = s"""
                    |{
                    |"desc": "Updated descr"
                    |}
        """.stripMargin
      val resp = await(updateOrganization(testOrg1.id, body, o1testUserToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(FORBIDDEN) }
    }

    "not search orgs by regular org admin" in {
      val resp = await(WS.url(ORGS_BASE_URL).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> o1testUserToken).get())

      withClue("Response body: "+resp.body) { resp.status mustBe(FORBIDDEN) }
    }

    "not get other org details by regular org admin" in {
      val resp = await(WS.url(ORGS_BASE_URL + "/" + testOrg1.id).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> o2testUserToken).get())

      withClue("Response body: "+resp.body) { resp.status mustBe(FORBIDDEN) }
    }

    "get own org details by regular org admin" in {
      val resp = await(WS.url(ORGS_BASE_URL + "/" + testOrg1.id).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> o1testUserToken).get())

      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      (resp.json \ "id").as[String] must be (testOrg1.id)
      (resp.json \ "name").as[String] must be (testOrg1.name)
    }

    "not create organization with the same name which is already exists" in {
      val resp = await(createOrganization(testOrg1.name))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
    }

    "not update organization name to the one which is already exists" in {
      val body = s"""
                    |{"name": "${testOrg1.name}",
                    |"desc": "First organization",
                    |"applicationIds": ["111", "222"]
                    |}
        """.stripMargin
      val resp = await(updateOrganization(testOrg2.id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
    }

    "create organization with the same name if the previous organization is deleted" in {
      val actualOrg = await(orgsQueryService.get(org1Id)).value
      actualOrg.status must be (OrganizationStatus.DELETED)

      val resp = await(createOrganization(actualOrg.name))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
    }

    "update organization name to the one which is already exists if the previous organization is deleted" in {
      val deleteOrg1Resp = await(WS.url(ORGS_BASE_URL + "/" + testOrg1.id).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).delete())
      withClue("Response body: "+deleteOrg1Resp.body) { deleteOrg1Resp.status mustBe(OK) }
      val deletedOrg = await(orgsQueryService.get(testOrg1.id)).value
      deletedOrg.status must be (OrganizationStatus.DELETED)

      val body = s"""
                    |{"name": "${testOrg1.name}",
                    |"desc": "First organization",
                    |"applicationIds": ["111", "222"]
                    |}
        """.stripMargin
      val resp = await(updateOrganization(testOrg2.id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
    }

  }

  private def createOrganizations(amount: Int): Future[Seq[WSResponse]] = {
    val OrganizationWsResponses = (1 to amount).map { i =>
      createOrganization(organizationName + i)
    }
    Future.sequence(OrganizationWsResponses)
  }

  private def createOrganization(orgName: String): Future[WSResponse] = {
    WS.url(ORGS_BASE_URL).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).post(
      s"""
         |{"name": "${orgName}",
         |"desc": "First org",
         |"parentOrganizationId": "$rootOrgId",
         |"signUpEnabled": false,
         |"signUpGroupIds": []
         |}
        """.stripMargin
    )
  }

  private def updateOrganization(orgId: String, body: String, token: String): Future[WSResponse] = {
    WS.url(ORGS_BASE_URL + "/" + orgId).withHeaders(("Content-Type", "application/json")).
      withQueryString("access_token" -> token).put(body)
  }
}
