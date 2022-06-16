package com.sentrana.umserver.controllers

import java.io.{StringWriter, ByteArrayInputStream, InputStream, StringReader}
import java.net.{URLDecoder, URLEncoder, URI, URL}
import java.nio.charset.Charset
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.ZonedDateTime
import java.util.zip.{Inflater, Deflater}

import com.sentrana.umserver.services.{AuthenticationService, SamlProviderQueryService}
import com.sentrana.umserver.{UmSettings, IntegrationTestUtils, OneServerWithMongo, WithAdminUser}
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.StringEscapeUtils
import org.apache.http.client.utils.URLEncodedUtils
import org.joda.time.DateTime
import org.opensaml.DefaultBootstrap
import org.opensaml.common.SAMLObjectBuilder
import org.opensaml.saml2.core._
import org.opensaml.xml.security.credential.BasicCredential
import org.opensaml.xml.signature.{Signer, SignatureConstants, Signature}
import org.opensaml.xml.util.XMLHelper
import org.scalatestplus.play.PlaySpec
import play.api.http.{MimeTypes, ContentTypes, HeaderNames}
import play.api.libs.ws.WS
import play.api.test.Helpers._

import scala.io.Source
import scala.xml.{XML}

/**
  * Created by Paul Lysak on 18.05.16.
  */
class SamlProviderControllerSpec extends PlaySpec with OneServerWithMongo with WithAdminUser {
  import IntegrationTestUtils.IT_PREFIX

  private def samlBaseUrl(orgId: String = rootOrg.id) = s"$baseUrl/orgs/${orgId}/saml"
  private def spBaseUrl(orgId: String = rootOrg.id) = s"${samlBaseUrl(orgId)}/providers"
  private def callbackUrl(provId: String) = s"$baseUrl/saml/$provId/handle"

  private lazy val spqService = app.injector.instanceOf(classOf[SamlProviderQueryService])
  private lazy val umSettings = app.injector.instanceOf(classOf[UmSettings])

  private lazy val sampleUser1 = itUtils.createTestUser("sampleUser1")
  private lazy val sampleGroup1 = itUtils.createTestGroup("sampleGroup1")

  private var sp1Id: String = _
  private val sp1Name = "sample_prov"
  private val sampleUrl1 = "http://saml.sample.com"
  private val sampleUrl2 = "http://saml2.sample.com"
  private val sampleCert1 = "TODO key"

  private lazy val app1 = itUtils.createTestApplicationInfo(name = "sampleApp1", url = Option("http://sample_app_url"))

  private val sampleCert2 = Source.fromURI(this.getClass.getResource("/saml_sample_cert.pem").toURI).mkString

  "SamlProviderController" must {
    "create SamlProvider" in {
      val resp = await(WS.url(spBaseUrl()).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).post(
        s"""
          |{
          |"name": "sample provider",
          |"desc": "sample desc",
          |"url": "$sampleUrl1",
          |"idProviderCertificate": "$sampleCert1",
          |"serviceProvider": "myself1",
          |"organizationId": "${rootOrg.id}"
          |}
        """.stripMargin
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      sp1Id = (resp.json \ "id").as[String]
      sp1Id must not be empty

      val actualSp = await(spqService.getMandatory(rootOrg.id, sp1Id))
      actualSp.id must be (sp1Id)
      actualSp.name must be ("sample provider")
      actualSp.desc.value must be ("sample desc")
      actualSp.url must be (sampleUrl1)
      actualSp.idProviderCertificate must be (sampleCert1)
      actualSp.serviceProvider must be ("myself1")
      actualSp.organizationId must be (rootOrg.id)
    }

    "not create SamlProvider with existing name" in {
      val resp = await(WS.url(spBaseUrl()).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).post(
        s"""
          |{
          |"name": "sample provider",
          |"desc": "sample desc",
          |"url": "$sampleUrl1",
          |"idProviderCertificate": "$sampleCert1",
          |"serviceProvider": "myself1",
          |"organizationId": "${rootOrg.id}"
          |}
        """.stripMargin
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
    }


    "get SamlProvider" in {
      val resp = await(WS.url(spBaseUrl() + "/" + sp1Id).withQueryString("access_token" -> adminToken).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      (resp.json \ "id").as[String] must be (sp1Id)
      (resp.json \ "name").as[String] must be ("sample provider")
      (resp.json \ "desc").as[String] must be ("sample desc")
      (resp.json \ "url").as[String] must be (sampleUrl1)
      (resp.json \ "idProviderCertificate").as[String] must be (sampleCert1)
      (resp.json \ "serviceProvider").as[String] must be ("myself1")
      (resp.json \ "organizationId").as[String] must be (rootOrg.id)
    }

    "update SamlProvider" in {
      val resp = await(WS.url(spBaseUrl() + "/" + sp1Id).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).put(
        s"""
          |{
          |"name": "$sp1Name",
          |"desc": "desc1",
          |"url": "$sampleUrl2",
          |"idProviderCertificate": "${StringEscapeUtils.escapeJson(sampleCert2)}",
          |"serviceProvider": "myself2",
          |"defaultGroupIds": ["${sampleGroup1.id}"]
          |}
        """.stripMargin
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      (resp.json \ "id").as[String] must be (sp1Id)

      val actualSp = await(spqService.getMandatory(rootOrg.id, sp1Id))
      actualSp.id must be (sp1Id)
      actualSp.name must be (sp1Name)
      actualSp.desc.value must be ("desc1")
      actualSp.url must be (sampleUrl2)
      actualSp.idProviderCertificate must be (sampleCert2)
      actualSp.serviceProvider must be ("myself2")
    }

    "generate starter links" in {
      val targetUrl = "http://somewhere.com"
      val resp = await(WS.url(samlBaseUrl() + "/starters").
        withQueryString("access_token" -> adminToken, "targetAppName" -> targetUrl).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val actualLinks = (resp.json \ "links").as[Map[String, String]]
      actualLinks.keySet must be (Set(sp1Name))
      verifyStarterUrl(actualLinks.values.head, targetUrl)
    }

    "handle ID provider callback with too old assertion issue instant" in {
      val cbUrl = callbackUrl(sp1Name)
      val samlResp = sampleOpenSamlResp("sampleUserId", cbUrl, ZonedDateTime.now().minusMinutes(30).withFixedOffsetZone())
      val relState = "http://target.url.com"
      val resp = await(WS.url(cbUrl).post(Map("SAMLResponse" -> Seq(Base64.encodeBase64String(samlResp.getBytes)),
        "RelayState" -> Seq(relState)
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(400) }
      //must show human-readable error
      resp.header(HeaderNames.CONTENT_TYPE).value must startWith (MimeTypes.HTML)
    }

    "handle successful ID provider callback for new user without registration details" in {
      val cbUrl = callbackUrl(sp1Name)
      val samlResp = sampleOpenSamlResp("sampleUserId", cbUrl)
      val relState = app1.name
      val resp = await(WS.url(cbUrl).post(Map("SAMLResponse" -> Seq(Base64.encodeBase64String(samlResp.getBytes)),
        "RelayState" -> Seq(relState)
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      //should show form for entering registration details: username, first name, last name etc.
      resp.header(HeaderNames.CONTENT_TYPE).value must startWith (MimeTypes.HTML)
    }

    "handle ID provider callback with duplicate user name in registration details" in {
      val cbUrl = callbackUrl(sp1Name)
      val samlResp = sampleOpenSamlResp("sampleUserId", cbUrl)
      val relState = app1.name
      val resp = await(WS.url(cbUrl).post(Map(
        "SAMLResponse" -> Seq(Base64.encodeBase64String(samlResp.getBytes)),
        "RelayState" -> Seq(relState),
        "username" -> Seq(sampleUser1.username),
        "firstName" -> Seq("Sam"),
        "lastName" -> Seq("L"),
        "email" -> Seq("saml@somewhere.com"),
        "password" -> Seq("pwd")
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      //should show form for entering registration details again, as user with such userName already exists
      resp.header(HeaderNames.CONTENT_TYPE).value must startWith (MimeTypes.HTML)
    }

    "handle ID provider callback with correct registration details" in {
      val cbUrl = callbackUrl(sp1Name)
      val samlResp = sampleOpenSamlResp("sampleUserId", cbUrl)
      val relState = app1.name
      val resp = await(WS.url(cbUrl).withFollowRedirects(false).post(Map(
        "SAMLResponse" -> Seq(Base64.encodeBase64String(samlResp.getBytes)),
        "RelayState" -> Seq(relState),
        "username" -> Seq("samlUser1"),
        "firstName" -> Seq("Sam"),
        "lastName" -> Seq("L"),
        "email" -> Seq("saml@somewhere.com"),
        "password" -> Seq("pwd")
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(SEE_OTHER) }

      val locationUrl = new URL(resp.header(HeaderNames.LOCATION).value)
      locationUrl.toString must startWith (app1.url.value)
      val queryParams = parseQueryString(locationUrl.getQuery)
      val actualToken = queryParams.get("access_token").flatMap(_.headOption).value
      actualToken must not be (empty)

      val actualUser = await(authService.validateToken(actualToken)).value
      actualUser.username must be ("samlUser1")
      actualUser.externalId.value must be ("sampleUserId")
      actualUser.groupIds must contain only (sampleGroup1.id)
    }

    "handle successful ID provider callback for existing user" in {
      val cbUrl = callbackUrl(sp1Name)
      val samlResp = sampleOpenSamlResp("sampleUserId", cbUrl)
      val relState = app1.name
      val resp = await(WS.url(cbUrl).withFollowRedirects(false).post(Map("SAMLResponse" -> Seq(Base64.encodeBase64String(samlResp.getBytes)),
        "RelayState" -> Seq(relState)
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(SEE_OTHER) }

      val locationUrl = new URL(resp.header(HeaderNames.LOCATION).value)
      locationUrl.toString must startWith (app1.url.value)
      val queryParams = parseQueryString(locationUrl.getQuery)
      val actualToken = queryParams.get("access_token").flatMap(_.headOption).value
      actualToken must not be (empty)

      val actualUser = await(authService.validateToken(actualToken)).value
      actualUser.username must be ("samlUser1")
      actualUser.externalId.value must be ("sampleUserId")
    }

    "delete SamlProvider" in {
      val resp = await(WS.url(spBaseUrl() + "/" + sp1Id).withQueryString("access_token" -> adminToken).delete())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      await(spqService.get(rootOrg.id, sp1Id)) must be (empty)
    }
  }

  private def parseQueryString(query: String): Map[String, Seq[String]] = {
    query.split("&").toSeq.map(p => p.split("=", 2).toList match {
        case k :: v :: Nil => k -> Seq(v)
        case k :: tail => k -> Nil
        case _ => "" -> Nil
      }).filter(_._1.nonEmpty).groupBy(_._1).map({case (k, vs) => k -> vs.flatMap(_._2)})
  }

  private def verifyStarterUrl(urlStr: String, targetUrl: String) = {
    import scala.collection.JavaConversions._

    urlStr must startWith (sampleUrl2)
    val url = new URL(urlStr)
    val queryParams = URLEncodedUtils.parse(url.getQuery, Charset.forName("UTF-8")).map(nvp => nvp.getName -> nvp.getValue).toMap
    val samlReqEncoded = queryParams.get("SAMLRequest").value
    val relayState = queryParams.get("RelayState").value
    relayState must be (targetUrl)
    val samlReq = decompress(samlReqEncoded)
    val samlReqXml = XML.loadString(samlReq)
    samlReqXml.attributes.get("AssertionConsumerServiceURL").flatMap(_.headOption).foreach { u =>
      //url may be missing, so we check it only if it specified. TBD if we need it at all
      u.text must startWith (umSettings.urlWithPath)
    }
    val i = (samlReqXml \ "Issuer").text
    (samlReqXml \ "Issuer").text must be ("myself2")
  }

  private def decompress(in: String): String = {
    val inData = Base64.decodeBase64(in)
    val decompresser = new Inflater(true);
    decompresser.setInput(inData, 0, inData.length);
    //such buffer length is not safe for production, good enough for tests
    val buf = new Array[Byte](inData.length * 10)
    val resLen = decompresser.inflate(buf);
    decompresser.end()
    val decoded = new String(buf, 0, resLen, "UTF-8");
    decoded
  }

  private def sampleOpenSamlResp(userId: String, callbackUrl: String, now: ZonedDateTime = ZonedDateTime.now().withFixedOffsetZone()): String = {
    val idpUrl = "https://idp.example.org/SAML2"

    DefaultBootstrap.bootstrap()
    val bf = org.opensaml.xml.Configuration.getBuilderFactory()

    val issuer = bf.getBuilder(Issuer.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[Issuer]].buildObject()
    issuer.setValue(idpUrl)

    val nameId = bf.getBuilder(NameID.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[NameID]].buildObject()
    nameId.setValue(userId)
    val subjConfData = bf.getBuilder(SubjectConfirmationData.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[SubjectConfirmationData]].buildObject()
    subjConfData.setInResponseTo("identifier_1")
    subjConfData.setRecipient(callbackUrl)
    val subjConf = bf.getBuilder(SubjectConfirmation.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[SubjectConfirmation]].buildObject()
    subjConf.setSubjectConfirmationData(subjConfData)
    subjConf.setMethod("urn:oasis:names:tc:SAML:2.0:cm:bearer")
    val subject = bf.getBuilder(Subject.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[Subject]].buildObject()
    subject.setNameID(nameId)
    subject.getSubjectConfirmations.add(subjConf)

    val audience = bf.getBuilder(Audience.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[Audience]].buildObject()
    audience.setAudienceURI("https://sp.example.com/SAML2")
    val audRestr = bf.getBuilder(AudienceRestriction.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[AudienceRestriction]].buildObject()
    audRestr.getAudiences.add(audience)
    val conditions = bf.getBuilder(Conditions.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[Conditions]].buildObject()
    conditions.setNotBefore(DateTime.parse("2015-12-05T09:17:05Z"))
    conditions.setNotOnOrAfter(DateTime.parse("2020-12-05T09:27:05Z"))
    conditions.getAudienceRestrictions


    val authnContextClassRef = bf.getBuilder(AuthnContextClassRef.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[AuthnContextClassRef]].buildObject()
    authnContextClassRef.setAuthnContextClassRef("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport")
    val authnContext = bf.getBuilder(AuthnContext.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[AuthnContext]].buildObject()
    authnContext.setAuthnContextClassRef(authnContextClassRef)
    val authnStatement = bf.getBuilder(AuthnStatement.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[AuthnStatement]].buildObject()
    authnStatement.setAuthnInstant(DateTime.parse(now.toString))
    authnStatement.setSessionIndex("identifier_3")
    authnStatement.setAuthnContext(authnContext)

    val assertion = bf.getBuilder(Assertion.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[Assertion]].buildObject()
    assertion.setID("myAssertionId")
    assertion.setIssueInstant(DateTime.parse(now.toString))
    assertion.setIssuer(issuer)
    assertion.setSubject(subject)
    assertion.setConditions(conditions)
    assertion.getAuthnStatements.add(authnStatement)

    val privKeyStr = Source.fromURI(this.getClass.getResource("/saml_sample_key.pem").toURI).getLines().filterNot(_.startsWith("--")).mkString
    val privKeyBytes = Base64.decodeBase64(privKeyStr)
    val privSpec = new PKCS8EncodedKeySpec(privKeyBytes);
    val keyFactory = KeyFactory.getInstance("RSA");
    val privKey = keyFactory.generatePrivate(privSpec);

    val cred = new BasicCredential()
    cred.setPrivateKey(privKey)

    val signature = bf.getBuilder(Signature.DEFAULT_ELEMENT_NAME)
                        .buildObject(Signature.DEFAULT_ELEMENT_NAME).asInstanceOf[Signature];

    signature.setSigningCredential(cred);
    signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA1);
    signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

    assertion.setSignature(signature)

    org.opensaml.xml.Configuration.getMarshallerFactory().getMarshaller(assertion).marshall(assertion);

    Signer.signObject(signature);

    val respIssuer = bf.getBuilder(Issuer.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[Issuer]].buildObject()
    respIssuer.setValue(idpUrl)
    val respStatusCode = bf.getBuilder(StatusCode.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[StatusCode]].buildObject()
    respStatusCode.setValue("urn:oasis:names:tc:SAML:2.0:status:Success")
    val respStatus = bf.getBuilder(Status.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[Status]].buildObject()
    respStatus.setStatusCode(respStatusCode)
    val resp = bf.getBuilder(Response.DEFAULT_ELEMENT_NAME).asInstanceOf[SAMLObjectBuilder[Response]].buildObject()
    resp.setID("identifier_2")
    resp.setDestination(callbackUrl)
    resp.setIssueInstant(DateTime.parse(now.toString))
    resp.setStatus(respStatus)
    resp.getAssertions.add(assertion)

    val respXml = org.opensaml.xml.Configuration.getMarshallerFactory().getMarshaller(resp) marshall(resp);

    XMLHelper.nodeToString(respXml)
  }
}
