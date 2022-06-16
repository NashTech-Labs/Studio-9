package com.sentrana.umserver.orgs

import com.sentrana.umserver.{OneServerWithMongo, WithAdminUser}
import org.scalatestplus.play.PlaySpec
import play.api.libs.ws.WS
import play.api.test.Helpers._

/**
  * Make sure that organization hierarchies work properly for detecting access rights
  *
  * Created by Paul Lysak on 28.04.16.
  */
class OrgsHierarchySpec extends PlaySpec with OneServerWithMongo with WithAdminUser {
  private def usersBaseUrl(orgId: String) = s"$baseUrl/orgs/$orgId/users"

  private lazy val org1 = itUtils.createTestOrg("org1", rootOrg.id)
  private lazy val org2 = itUtils.createTestOrg("org2", rootOrg.id)

  private lazy val org1User1 = itUtils.createTestUser("org1User1", orgId = org1.id)
  private lazy val org2User1 = itUtils.createTestUser("org2User1", orgId = org2.id)

  private lazy val admin1Org1 = itUtils.createTestUser("admin1org1", groupIds = Set(adminGroup.id), orgId = org1.id)
  private lazy val admin1Org1Token = authService.issueToken(admin1Org1)._1


  private def userCreationJson(orgId: String, userName: String = "u1"): String = {
    s"""
       |{"username": "$userName",
       |"email": "$userName@host.com",
       |"firstName": "John",
       |"lastName": "Doe",
       |"password": "knockKnock",
       |"organizationId": "$orgId",
       |"groupIds": [],
       |"dataFilterInstances": []
       |}
        """.stripMargin
  }

  def testUserCreation(userName: String, orgId: String, accessToken: String, expectedStatus: Int): Unit = {
      val resp = await(WS.url(usersBaseUrl(orgId)).withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> accessToken).post(
        userCreationJson(orgId, userName)
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(expectedStatus) }
  }

  def testUserRetrieval(userId: String, orgId: String, accessToken: String, expectedStatus: Int): Unit = {
       val resp = await(WS.url(usersBaseUrl(orgId) + "/" + userId).withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> accessToken).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(expectedStatus) }
  }

  "Admin from root org" must {
    "create another user in root org" in {
      testUserCreation("aRoRu1", rootOrg.id, adminToken, OK)
    }

    "create user in org1" in {
      testUserCreation("aRo1u1", org1.id, adminToken, OK)
    }

    "create user in org2" in {
      testUserCreation("aRo2u1", org2.id, adminToken, OK)
    }

    "retrieve user from org1 with org1 prefix" in {
      testUserRetrieval(org1User1.id, org1.id, adminToken, OK)
    }

    "retrieve user from org1 with root prefix" in {
      testUserRetrieval(org1User1.id, rootOrg.id, adminToken, OK)
    }

    "retrieve user from root org with root org prefix" in {
      testUserRetrieval(adminUser.id, rootOrg.id, adminToken, OK)
    }

    "not retrieve user from root org with org1 prefix" in {
      testUserRetrieval(adminUser.id, org1.id, adminToken, NOT_FOUND)
    }
  }

  "Admin from org1" must {
    "not create user in root org" in {
      testUserCreation("a1oRu1", rootOrg.id, admin1Org1Token, FORBIDDEN)
    }

    "create user in org1" in {
      testUserCreation("a1o1u1", org1.id, admin1Org1Token, OK)
    }

    "not create user in org2" in {
      testUserCreation("a1o2u1", org2.id, admin1Org1Token, FORBIDDEN)
    }

    "retrieve user from org1 with org1 prefix" in {
      testUserRetrieval(org1User1.id, org1.id, admin1Org1Token, OK)
    }

    "not retrieve user from org1 with root prefix" in {
      testUserRetrieval(org1User1.id, rootOrg.id, admin1Org1Token, FORBIDDEN)
    }

    "not retrieve user from root org with root org prefix" in {
      testUserRetrieval(adminUser.id, rootOrg.id, admin1Org1Token, FORBIDDEN)
    }

    "not retrieve user from root org with org1 prefix" in {
      testUserRetrieval(adminUser.id, org1.id, admin1Org1Token, NOT_FOUND)
    }
  }
}
