package com.sentrana.um.client.play

import com.sentrana.um.client.play.exceptions.{ UmAccessDeniedException, UmAuthenticationException, UmServerException }
import com.sentrana.umserver.shared.dtos.enums.UserStatus
import com.sentrana.umserver.shared.dtos.{ Organization, User, UserGroup }
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.{ BeforeAndAfterEach, FlatSpec, Matchers, OptionValues }
import play.api.cache.CacheApi
import play.api.http.{ Status, Writeable }
import play.api.libs.json._
import play.api.libs.ws.{ WSClient, WSRequest, WSResponse }
import play.api.test.Helpers._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Created by Alexander on 05.05.2016.
 */
class UmClientImplSpec extends FlatSpec with Matchers with BeforeAndAfterEach with OptionValues {
  private val umServiceUrl = "umServiceUrl"
  private val clientId = "clientId"
  private val clientSecret = "clientSecret"
  private val wsClientMock = mock(classOf[WSClient])
  private val cacheMock = mock(classOf[CacheApi])
  private val umClient = new UmClientImpl(umServiceUrl, clientId, clientSecret, cacheMock, wsClientMock)

  private val token = "testToken"
  private val testOrganization = Organization("_testOrgId", "organizationName")

  private val testUser = User(
    "testUserId",
    "testUserName",
    "testEmail",
    "testFirstName",
    "testLastName",
    UserStatus.ACTIVE,
    userGroupIds = Set(),
    permissions = Set(),
    organizationId = testOrganization.id,
    dataFilterInstances = Set(),
    fromRootOrg = false
  )

  private val testUserGroup = UserGroup(
    "testUserGroupId",
    testOrganization.id,
    None,
    "testUserGroupName",
    None,
    Set(),
    false
  )

  "validateAccessToken" should "return None on BAD_REQUEST" in {
    val (requestMock, responseMock) = prepareMocks(umClient.baseUrl + s"/token/$token/user")
    when(requestMock.get()).thenReturn(Future.successful(responseMock))
    when(responseMock.status).thenReturn(Status.BAD_REQUEST)
    when(cacheMock.get[User](token)).thenReturn(None)

    await(umClient.validateAccessToken(token)) shouldBe empty
  }

  it should "get result from a cache with no call to um-server" in {
    when(cacheMock.get[User](token)).thenReturn(Option(testUser))
    await(umClient.validateAccessToken(token)) shouldBe Option(testUser)

    verifyZeroInteractions(wsClientMock)
  }

  it should "call um-server if no result is in a cache" in {
    import JsonFormats._
    val (requestMock, responseMock) = prepareMocks(umClient.baseUrl + s"/token/$token/user")
    val jsValueMock = mock(classOf[JsValue])
    when(requestMock.get()).thenReturn(Future.successful(responseMock))
    when(responseMock.status).thenReturn(Status.OK)
    when(responseMock.json).thenReturn(jsValueMock)
    when(responseMock.json.as[User]).thenReturn(testUser)
    when(cacheMock.get[User](token)).thenReturn(None)
    await(umClient.validateAccessToken(token)) shouldBe Option(testUser)

    verify(wsClientMock).url(umClient.baseUrl + s"/token/$token/user")
    ()
  }

  it should "throw UmServerException on UNAUTHORIZED" in {
    val (requestMock, responseMock) = prepareMocks(umClient.baseUrl + s"/token/$token/user")
    when(requestMock.get()).thenReturn(Future.successful(responseMock))
    when(responseMock.status).thenReturn(Status.UNAUTHORIZED)
    when(cacheMock.get[User](token)).thenReturn(None)

    intercept[UmServerException] {
      await(umClient.validateAccessToken(token))
    }
    ()
  }

  "getRootOrg" should "throw exception because there is no such application_token" in {
    val umClientSpy = spy(umClient)
    doReturn(Future.successful(None)).when(umClientSpy).getApplicationAccessToken()
    intercept[UmServerException] {
      await(umClientSpy.getRootOrg())
    }
    ()
  }

  it should "throw UmAuthenticationException because server returned UNAUTHORIZED" in {
    val (requestMock, responseMock) = prepareMocks(umClient.baseUrl + "/orgs/root")

    when(requestMock.get()).thenReturn(Future.successful(responseMock))
    when(requestMock.withHeaders(org.mockito.Matchers.anyVararg[(String, String)]())).thenReturn(requestMock)
    when(responseMock.status).thenReturn(Status.UNAUTHORIZED)
    when(responseMock.body).thenReturn("")

    val umClientSpy = spy(umClient)
    doReturn(Future.successful(Some(token))).when(umClientSpy).getApplicationAccessToken()

    intercept[UmAuthenticationException] {
      await(umClientSpy.getRootOrg())
    }
    ()
  }

  it should "return valid organization" in {
    val (requestMock, responseMock) = prepareMocks(umClient.baseUrl + "/orgs/root")

    when(requestMock.get()).thenReturn(Future.successful(responseMock))
    when(requestMock.withHeaders(anyVararg[(String, String)]())).thenReturn(requestMock)
    when(responseMock.status).thenReturn(Status.UNAUTHORIZED, Status.OK)

    when(responseMock.json).thenReturn(
      new JsObject(Map(
        "id" -> JsString(testOrganization.id),
        "name" -> JsString(testOrganization.name),
        "status" -> JsString(testOrganization.status.toString),
        "applicationIds" -> JsArray(Seq()),
        "created" -> JsString(testOrganization.created.toString),
        "updated" -> JsString(testOrganization.updated.toString),
        "dataFilterInstances" -> JsArray(),
        "signUpEnabled" -> JsBoolean(false),
        "signUpGroupIds" -> JsArray()
      ))
    )

    val umClientSpy = spy(umClient)
    doReturn(Future.successful(Some(token))).when(umClientSpy).getApplicationAccessToken()

    val org = await(umClientSpy.getRootOrg())
    org.id shouldBe testOrganization.id
    org.name shouldBe testOrganization.name
    org.status shouldBe testOrganization.status
    org.applicationIds shouldBe testOrganization.applicationIds
  }

  "getUser" should "throw exception because no such access_token" in {
    val umClientSpy = spy(umClient)
    doReturn(Future.successful(None)).when(umClientSpy).getApplicationAccessToken()

    intercept[UmServerException] {
      await(umClientSpy.getUser("userId"))
    }
    ()
  }

  it should "throw UmAuthenticationException when server returns UNAUTHORIZED" in {
    intercept[UmAuthenticationException] {
      prepareMocksToGetUserWithExpectedStatus(Status.UNAUTHORIZED)
    }
    ()
  }

  it should "throw UmAccessDeniedException when server returns FORBIDDEN" in {
    intercept[UmAccessDeniedException] {
      prepareMocksToGetUserWithExpectedStatus(Status.FORBIDDEN)
    }
    ()
  }

  it should "return valid user" in {
    val (requestMock, responseMock) = prepareMocks(umClient.baseUrl + s"/orgs/${testOrganization.id}/users/${testUser.id}")

    val umClientSpy = spy(umClient)

    doReturn(Future.successful(testOrganization)).when(umClientSpy).getRootOrg()
    when(requestMock.get()).thenReturn(Future.successful(responseMock))
    when(requestMock.withHeaders(anyVararg[(String, String)]())).thenReturn(requestMock)
    when(responseMock.status).thenReturn(Status.UNAUTHORIZED, Status.OK)

    when(responseMock.json).thenReturn(
      new JsObject(Map(
        "id" -> JsString(testUser.id),
        "username" -> JsString(testUser.username),
        "firstName" -> JsString(testUser.firstName),
        "lastName" -> JsString(testUser.lastName),
        "fromRootOrg" -> JsBoolean(testUser.fromRootOrg),
        "email" -> JsString(testUser.email),
        "status" -> JsString(testUser.status.toString),
        "userGroupIds" -> JsArray(Seq()),
        "permissions" -> JsArray(Seq()),
        "organizationId" -> JsString(testUser.organizationId),
        "updated" -> JsString(testUser.updated.toString),
        "created" -> JsString(testUser.created.toString),
        "dataFilterInstances" -> JsArray()
      ))
    )

    doReturn(Future.successful(Some(token))).when(umClientSpy).getApplicationAccessToken()

    val actualUser = await(umClientSpy.getUser(testUser.id)).value
    actualUser.id shouldBe testUser.id
    actualUser.username shouldBe testUser.username
    actualUser.firstName shouldBe testUser.firstName
    actualUser.lastName shouldBe testUser.lastName
    actualUser.email shouldBe testUser.email
    actualUser.userGroupIds shouldBe testUser.userGroupIds
    actualUser.organizationId shouldBe testUser.organizationId
    actualUser.fromRootOrg shouldBe testUser.fromRootOrg
  }

  "getUserGroup" should "throw exception because no such access_token" in {
    val umClientSpy = spy(umClient)
    doReturn(Future.successful(None)).when(umClientSpy).getApplicationAccessToken()

    intercept[UmServerException] {
      await(umClientSpy.getUserGroup("userGroupId"))
    }
    ()
  }

  it should "throw UmAuthenticationException when server returns UNAUTHORIZED" in {
    intercept[UmAuthenticationException] {
      prepareMocksToGetUserGroupWithExpectedStatus(Status.UNAUTHORIZED)
    }
    ()
  }

  it should "throw UmAccessDeniedException when server returns FORBIDDEN" in {
    intercept[UmAuthenticationException] {
      prepareMocksToGetUserGroupWithExpectedStatus(Status.FORBIDDEN)
    }
    ()
  }

  "signUp" should "throw UmServerException when servers response differs from OK or BAD_REQUEST" in {
    intercept[UmServerException] {
      val (requestMock, responseMock) = prepareMocks(umClient.baseUrl + s"/orgs/${testOrganization.id}/users/signup")

      when(requestMock.post(anyString)(any[Writeable[String]])).thenReturn(Future.successful(responseMock))
      when(requestMock.withHeaders(anyVararg[(String, String)]())).thenReturn(requestMock)
      when(responseMock.status).thenReturn(Status.UNAUTHORIZED)

      await(umClient.signUp(
        testOrganization.id,
        testUser.username,
        testUser.email,
        "",
        testUser.firstName,
        testUser.lastName
      ))
    }
    ()
  }

  "getUserGroup" should "return valid userGroup" in {
    val (requestMock, responseMock) = prepareMocks(umClient.baseUrl + s"/orgs/${testOrganization.id}/groups/${testUserGroup.id}")

    val umClientSpy = spy(umClient)
    doReturn(Future.successful(testOrganization)).when(umClientSpy).getRootOrg()
    when(requestMock.get()).thenReturn(Future.successful(responseMock))
    when(requestMock.withHeaders(anyVararg[(String, String)]())).thenReturn(requestMock)
    when(responseMock.status).thenReturn(Status.UNAUTHORIZED, Status.OK)

    when(responseMock.json).thenReturn(
      new JsObject(Map(
        "id" -> JsString(testUserGroup.id),
        "name" -> JsString(testUserGroup.name),
        "grantsPermissions" -> JsArray(Seq()),
        "organizationId" -> JsString(testUserGroup.organizationId),
        "forChildOrgs" -> JsBoolean(testUserGroup.forChildOrgs),
        "updated" -> JsString(testUserGroup.updated.toString),
        "created" -> JsString(testUserGroup.created.toString),
        "dataFilterInstances" -> JsArray()
      ))
    )

    doReturn(Future.successful(Some(token))).when(umClientSpy).getApplicationAccessToken()

    val actualUserGroup = await(umClientSpy.getUserGroup(testUserGroup.id)).value
    actualUserGroup.id shouldBe testUserGroup.id
    actualUserGroup.name shouldBe testUserGroup.name
    actualUserGroup.grantsPermissions shouldBe testUserGroup.grantsPermissions
    actualUserGroup.organizationId shouldBe testUserGroup.organizationId
    actualUserGroup.forChildOrgs shouldBe testUserGroup.forChildOrgs
    actualUserGroup.updated shouldBe testUserGroup.updated
    actualUserGroup.created shouldBe testUserGroup.created
  }

  private def prepareMocks(url: String): (WSRequest, WSResponse) = {
    val requestMock = mock(classOf[WSRequest])
    val responseMock = mock(classOf[WSResponse])
    when(wsClientMock.url(url)).thenReturn(requestMock)
    when(requestMock.withQueryString(any[Seq[(String, String)]](): _*)).thenReturn(requestMock)
    (requestMock, responseMock)
  }

  private def prepareMocksToGetUserWithExpectedStatus(status: Int): Option[User] = {
    val (requestMock, responseMock) = prepareMocks(umClient.baseUrl + s"/orgs/${testOrganization.id}/users/${testUser.id}")

    val umClientSpy = spy(umClient)
    doReturn(Future.successful(Some(token))).when(umClientSpy).getApplicationAccessToken()

    doReturn(Future.successful(testOrganization)).when(umClientSpy).getRootOrg()
    when(requestMock.get()).thenReturn(Future.successful(responseMock))
    when(requestMock.withHeaders(anyVararg[(String, String)]())).thenReturn(requestMock)
    when(responseMock.status).thenReturn(status)

    await(umClientSpy.getUser(testUser.id))
  }

  private def prepareMocksToGetUserGroupWithExpectedStatus(statusCode: Int): Option[UserGroup] = {
    val (requestMock, responseMock) = prepareMocks(umClient.baseUrl + s"/orgs/${testOrganization.id}/groups/${testUserGroup.id}")

    val umClientSpy = spy(umClient)
    doReturn(Future.successful(Some(token))).when(umClientSpy).getApplicationAccessToken()

    doReturn(Future.successful(testOrganization)).when(umClientSpy).getRootOrg()
    when(requestMock.get()).thenReturn(Future.successful(responseMock))
    when(requestMock.withHeaders(anyVararg[(String, String)]())).thenReturn(requestMock)
    when(responseMock.status).thenReturn(Status.UNAUTHORIZED)

    await(umClientSpy.getUserGroup(testUserGroup.id))
  }

  override protected def beforeEach(): Unit = {
    reset(wsClientMock)
    reset(cacheMock)
  }
}
