package com.sentrana.umserver.controllers

import com.sentrana.umserver.entities.UserEntity
import com.sentrana.umserver.services.{MongoDbService, UserGroupQueryService, UserService}
import com.sentrana.umserver.shared.JsonFormatsShared
import com.sentrana.umserver.shared.dtos.{UpdateUserAdminRequest, DataFilterInstance, Permission, UserGroup}
import com.sentrana.umserver.{IntegrationTestUtils, OneServerWithMongo, WithAdminUser}
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.libs.ws.{WS, WSResponse}
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by Paul Lysak on 18.04.16.
  */
class UserGroupControllerSpec extends PlaySpec with OneServerWithMongo with WithAdminUser {
  import IntegrationTestUtils.IT_PREFIX

  private implicit lazy val filterOperatorReads = new JsonFormatsShared().filterOperatorReads
  private implicit val dataFilterInstanceReads = Json.reads[DataFilterInstance]
  import com.sentrana.umserver.JsonFormats.userGroupFormat
  import com.sentrana.umserver.entities.MongoFormats.userGroupMongoFormat

  private def groupsBaseUrl(orgId: String) = s"$baseUrl/orgs/${orgId}/groups"

  private lazy val userService = app.injector.instanceOf(classOf[UserService])
  private lazy val groupQService = app.injector.instanceOf(classOf[UserGroupQueryService])
  private lazy val mongoService = app.injector.instanceOf(classOf[MongoDbService])

  private var group1Id: String = _
  private var group2Id: String = _
  private var userGroupCopyNameOrgOneId: String = _
  private var userGroupCopyNameOrgTwoId: String = _

  private val USER1_NAME = IT_PREFIX + "sampleUser1"
  private val userGroupBaseName = IT_PREFIX + "userGroup"
  private val anotherUserGroupName = "anotherUserGroupName"
  private val testUserGroupCopyName = "testUserGroupCopyName"

  private lazy val user1: UserEntity = itUtils.createTestUser(USER1_NAME)
  private lazy val orgOne = itUtils.createTestOrg("orgOne", rootOrg.id)
  private lazy val orgTwo = itUtils.createTestOrg("orgTwo", rootOrg.id)

  "GroupsController" must {
    "Create root group" in {
      rootOrg

      val resp = await(createUserGroup("parent group", rootOrg.id))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      group1Id = (resp.json \ "id").as[String]
      group1Id must not be empty

      val actualGroup = await(groupQService.getMandatory(rootOrg.id, group1Id))
      actualGroup.id must be (group1Id)
      actualGroup.organizationId must be (rootOrg.id)
      actualGroup.parentGroupId must be (empty)
      actualGroup.name must be ("parent group")
      actualGroup.desc must be (empty)
      actualGroup.grantsPermissions must be (Set(Permission("DO_THIS"), Permission("DO_THAT")))
      actualGroup.forChildOrgs must be (false)
      actualGroup.dataFilterInstances must be (empty)
    }

    "Get group details" in {
      val resp = await(WS.url(groupsBaseUrl(rootOrg.id) + "/" + group1Id).withQueryString("access_token" -> adminToken).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      (resp.json \ "id").as[String] must be(group1Id)
      (resp.json \ "organizationId").as[String] must be(rootOrg.id)
      (resp.json \ "parentGroupId").asOpt[String] must be(empty)
      (resp.json \ "name").as[String] must be("parent group")
      (resp.json \ "desc").asOpt[String] must be(empty)
      (resp.json \ "grantsPermissions" \\ "name").map(_.as[String]).toSet must be (Set("DO_THIS", "DO_THAT"))
      (resp.json \ "dataFilterInstances").as[Set[DataFilterInstance]] must be (empty)
    }

    "Update the group" in {
      val body = s"""
                    |{"name": "root group",
                    |"desc": "updated group",
                    |"grantsPermissions": [{"name": "DO_SOMETHING"}],
                    |"forChildOrgs": true
                    |}
        """.stripMargin
      val resp = await(updateUserGroup(rootOrg.id, group1Id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      (resp.json \ "id").as[String] must be (group1Id)

      val actualGroup = await(groupQService.getMandatory(rootOrg.id, group1Id))
      actualGroup.organizationId must be (rootOrg.id)
      actualGroup.parentGroupId must be (empty)
      actualGroup.name must be ("root group")
      actualGroup.desc.value must be ("updated group")
      actualGroup.grantsPermissions must be (Set(Permission("DO_SOMETHING")))
      actualGroup.forChildOrgs must be (true)
    }

    "Create child group" in {
      val resp = await(WS.url(groupsBaseUrl(rootOrg.id)).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).post(
        s"""
          |{"organizationId": "${rootOrg.id}",
          |"name": "subgroup1",
          |"parentGroupId": "$group1Id",
          |"grantsPermissions": [{"name": "DO_MORE_THINGS"}, {"name": "DO_THAT"}],
          |"dataFilterInstances": [],
          |"forChildOrgs": false
          |}
        """.stripMargin
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      group2Id = (resp.json \ "id").as[String]
      group2Id must not be empty

      val actualGroup = await(groupQService.getMandatory(rootOrg.id, group2Id))
      actualGroup.id must be (group2Id)
      actualGroup.organizationId must be (rootOrg.id)
      actualGroup.parentGroupId.value must be (group1Id)
      actualGroup.name must be ("subgroup1")
      actualGroup.desc must be (empty)
      actualGroup.grantsPermissions must be (Set(Permission("DO_MORE_THINGS"), Permission("DO_THAT")))
      actualGroup.forChildOrgs must be (false)
    }

    "Not be able to delete parent group when there's a child one" in {
      val resp = await(WS.url(groupsBaseUrl(rootOrg.id) + "/" + group1Id).withQueryString("access_token" -> adminToken).delete())
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
      await(groupQService.get(rootOrg.id, group1Id)) must not be (empty)
    }

    "Reset group parent" in {
      val body = s"""
                    |{
                    |"resetParentGroupId": true
                    |}
        """.stripMargin
      val resp = await(updateUserGroup(rootOrg.id, group2Id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val actualGroup = await(groupQService.getMandatory(rootOrg.id, group2Id))
      actualGroup.parentGroupId must be (empty)
    }

    "Delete group without children" in {
      val resp = await(WS.url(groupsBaseUrl(rootOrg.id) + "/" + group1Id).withQueryString("access_token" -> adminToken).delete())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      await(groupQService.get(rootOrg.id, group1Id)) must be (empty)
    }

    "Not be able to delete group if some user belongs to it" in {
      await(userService.update(rootOrg.id, user1.id, UpdateUserAdminRequest(groupIds = Some(Set(group2Id)))))
      val resp = await(WS.url(groupsBaseUrl(rootOrg.id) + "/" + group2Id).withQueryString("access_token" -> adminToken).delete())
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
      await(groupQService.get(rootOrg.id, group2Id)) must not be (empty)
    }

    "Delete group without users" in {
      await(userService.delete(rootOrg.id, user1.id))
      val resp = await(WS.url(groupsBaseUrl(rootOrg.id) + "/" + group2Id).withQueryString("access_token" -> adminToken).delete())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      await(groupQService.get(rootOrg.id, group2Id)) must be (empty)
    }

    "create additional groups" in {
      val createUserGroupResps = await(createUserGroups(6))
      createUserGroupResps.foreach(resp => (resp.json \ "id").as[String] must not be empty)
    }

    "find userGroups with limit" in {
      val userGroupLimit = 4
      val resp = await(WS.url(groupsBaseUrl(rootOrg.id)).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "limit" -> s"${userGroupLimit}"
        ).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      val userGroups = (resp.json \ "data").as[Seq[UserGroup]]
      userGroups.length mustBe userGroupLimit
    }

    "find userGroups with offset" in {
      val userGroupOffset = 5
      val groupsCount = await(mongoService.count[UserGroup](org.mongodb.scala.model.Filters.equal("organizationId", rootOrg.id)))
      val groups = await(mongoService.find[UserGroup](Document()).toFuture())

      val resp = await(WS.url(groupsBaseUrl(rootOrg.id)).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "offset" -> s"${userGroupOffset}"
        ).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      val userGroups = (resp.json \ "data").as[Seq[UserGroup]]
      userGroups.length mustBe (groupsCount - userGroupOffset)
    }

    "find userGroups with offset and limit" in {
      val userGroupLimit = 4
      val userGroupOffset = 2
      val resp = await(WS.url(groupsBaseUrl(rootOrg.id)).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "limit" -> s"${userGroupLimit}",
          "offset" -> s"${userGroupOffset}"
        ).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      val userGroups = (resp.json \ "data").as[Seq[UserGroup]]
      userGroups.length mustBe userGroupLimit
    }

    "not allow two or more userGroups with the same name in the scope of one organization" in {
      val userGroupOneResp = await(createUserGroup(testUserGroupCopyName, orgOne.id))
      withClue("Response body: "+userGroupOneResp.body) { userGroupOneResp.status mustBe(OK) }
      userGroupCopyNameOrgOneId = (userGroupOneResp.json \ "id").as[String]

      val userGroupTwoResp = await(createUserGroup(testUserGroupCopyName, orgOne.id))
      withClue("Response body: "+userGroupTwoResp.body) { userGroupTwoResp.status mustBe(BAD_REQUEST) }
    }

    "create userGroup with the same name across different organizations" in {
      val userGroupTwoResp = await(createUserGroup(testUserGroupCopyName, orgTwo.id))
      withClue("Response body: "+userGroupTwoResp.body) { userGroupTwoResp.status mustBe(OK) }
      userGroupCopyNameOrgTwoId = (userGroupTwoResp.json \ "id").as[String]
    }

    "not update userGroup`s name to the duplicate one in the organization" in {
      val anotherUserGroup = itUtils.createTestGroup(anotherUserGroupName, orgId = orgOne.id)
      val body = s"""
                    |{
                    |"name": "$testUserGroupCopyName"
                    |}
        """.stripMargin
      val resp = await(updateUserGroup(orgOne.id, anotherUserGroup.id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
    }

    "update userGroup`s name to the duplicate across different organizations" in {
      val anotherUserGroup = itUtils.createTestGroup(anotherUserGroupName+"_diff", orgId = orgTwo.id)

      val body = s"""
                    |{
                    |"name": "$anotherUserGroupName"
                    |}
        """.stripMargin
      val resp = await(updateUserGroup(orgTwo.id, anotherUserGroup.id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
    }
  }

  private def createUserGroup(userGroupName: String, organizationId: String): Future[WSResponse] = {
    WS.url(groupsBaseUrl(organizationId)).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).post(
      s"""
         |{"organizationId": "${organizationId}",
         |"grantsPermissions": [{"name": "DO_THIS"}, {"name": "DO_THAT"}],
         |"name": "$userGroupName",
         |"forChildOrgs": false,
         |"dataFilterInstances": []
         |}
        """.stripMargin
    )
  }

  private def createUserGroups(amount: Int): Future[Seq[WSResponse]] = {
    val userGroupsWsResponses = (1 to amount).map { i =>
      createUserGroup(userGroupBaseName + i, rootOrg.id)
    }
    Future.sequence(userGroupsWsResponses)
  }

  private def updateUserGroup(orgId: String, userGroupId: String, body: String, token: String): Future[WSResponse] = {
    WS.url(groupsBaseUrl(orgId) + "/" + userGroupId).
      withHeaders(("Content-Type", "application/json")).
      withQueryString("access_token" -> token)
      .put(body)
  }
}
