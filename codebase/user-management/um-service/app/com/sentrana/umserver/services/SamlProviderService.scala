package com.sentrana.umserver.services

import java.io.{ StringWriter, ByteArrayInputStream }
import java.net.URLEncoder
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.spec.X509EncodedKeySpec
import java.time.{ ZonedDateTime, Clock }
import java.util.UUID
import java.util.zip.{ Inflater, Deflater }
import javax.inject.{ Inject, Singleton }
import javax.xml.parsers.DocumentBuilderFactory

import com.sentrana.umserver.UmSettings
import com.sentrana.umserver.dtos.{ SamlProvider, UpdateSamlProviderRequest, CreateSamlProviderRequest }
import com.sentrana.umserver.entities.{ MongoEntityFormat, UserEntity }
import com.sentrana.umserver.exceptions.{ ValidationException, SamlResponseException }
import org.apache.commons.codec.binary.Base64
import org.bson.BsonDocument
import org.joda.time.format.{ ISODateTimeFormat, DateTimeFormatter }
import org.mongodb.scala.bson.conversions
import org.opensaml.DefaultBootstrap
import org.opensaml.saml2.core.{ Issuer, AuthnRequest, Response }
import org.opensaml.xml.security.credential.{ Credential, BasicCredential }
import org.opensaml.xml.signature.SignatureValidator
import org.opensaml.xml.util.XMLHelper

import scala.async.Async
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by Paul Lysak on 18.05.16.
 */
@Singleton
class SamlProviderService @Inject() (
    clock:                       Clock,
    val orgQueryService:         OrganizationQueryService,
    userService:                 UserService,
    userQueryService:            UserQueryService,
    qService:                    SamlProviderQueryService,
    settings:                    UmSettings,
    implicit val mongoDbService: MongoDbService
) extends EntityMTCommandService[CreateSamlProviderRequest, UpdateSamlProviderRequest, SamlProvider] {
  import SamlProviderService.SamlUserData

  DefaultBootstrap.bootstrap()

  override protected implicit val mongoEntityFormat: MongoEntityFormat[SamlProvider] = com.sentrana.umserver.entities.MongoFormats.samlProviderMongoFormat

  override def create(orgId: String, req: CreateSamlProviderRequest): Future[SamlProvider] = {
    val now = ZonedDateTime.now(clock)
    val entity = SamlProvider(
      id                    = mongoDbService.generateId,
      name                  = req.name,
      desc                  = req.desc,
      url                   = req.url,
      idProviderCertificate = req.idProviderCertificate,
      organizationId        = orgId,
      serviceProvider       = req.serviceProvider,
      defaultGroupIds       = req.defaultGroupIds.getOrElse(Set()),
      created               = now,
      updated               = now
    )
    Async.async {
      Async.await(validateSamlProviderName(req.name))
      Async.await(mongoDbService.save(entity))
    }
  }

  override def update(orgId: String, id: String, req: UpdateSamlProviderRequest): Future[SamlProvider] = {
    val now = ZonedDateTime.now(clock)
    Async.async {
      val sp = Async.await(qService.getMandatory(orgId, id))

      val spUpd = sp.copy(
        name                  = req.name.getOrElse(sp.name),
        desc                  = req.desc.orElse(sp.desc),
        url                   = req.url.getOrElse(sp.url),
        idProviderCertificate = req.idProviderCertificate.getOrElse(sp.idProviderCertificate),
        serviceProvider       = req.serviceProvider.getOrElse(sp.serviceProvider),
        defaultGroupIds       = req.defaultGroupIds.getOrElse(sp.defaultGroupIds),
        updated               = now
      )
      Async.await(validateSamlProviderName(spUpd.name, Option(sp.name)))
      Async.await(mongoDbService.update(spUpd, orgScope(orgId)))
    }
  }

  def list(orgId: String, skip: Int = 0, limit: Int = 10): Future[Seq[SamlProvider]] = {
    import org.mongodb.scala.model.Filters._
    mongoDbService.find[SamlProvider](equal("organizationId", orgId), offset = skip, limit = limit).toFuture()
  }

  def buildStartLink(samlProvider: SamlProvider, targetAppName: String): String = {
    val bf = org.opensaml.xml.Configuration.getBuilderFactory()
    val issuer = bf.getBuilder(Issuer.DEFAULT_ELEMENT_NAME).buildObject(Issuer.DEFAULT_ELEMENT_NAME).asInstanceOf[Issuer]
    issuer.setValue(samlProvider.serviceProvider)
    val authnRequest = bf.getBuilder(AuthnRequest.DEFAULT_ELEMENT_NAME).buildObject(AuthnRequest.DEFAULT_ELEMENT_NAME).asInstanceOf[AuthnRequest]
    authnRequest.setID(generateRequestId())
    //consumerServiceURL is optional, service provider can get it from its settings. It's more for double-checking. May need to enable it back after fixing config on SAML provider side
    //    authnRequest.setAssertionConsumerServiceURL(samlResultHandlingUrl(samlProvider.id))

    authnRequest.setIssuer(issuer)
    authnRequest.setIssueInstant(org.joda.time.DateTime.now())

    val authnXml = org.opensaml.xml.Configuration.getMarshallerFactory().getMarshaller(authnRequest) marshall (authnRequest);

    val authnReqStr = XMLHelper.nodeToString(authnXml)
    val samlReq = URLEncoder.encode(Base64.encodeBase64String(compress(authnReqStr)), "UTF-8")
    val relayState = URLEncoder.encode(targetAppName, "UTF-8")

    val link = s"${samlProvider.url}?SAMLRequest=$samlReq&RelayState=$relayState"
    link
  }

  /**
   * As a response to SAML identity provider callback:
   * - identify assotiated user by identity provider id + nameId, return those user
   * - make sure this is new user, return what's going to be its external Id
   * - return error in Future if request can't be validated (for example, signature check fails)
   *
   * @param provName
   * @param samlResponse
   * @param stateRelay
   * @return
   */
  def getUserFromSamlResponse(provName: String, samlResponse: String, stateRelay: String): Future[Either[SamlUserData, UserEntity]] = {
    Async.async {
      val prov = Async.await(qService.getByName(rootOrgId, provName)).
        getOrElse(throw new SamlResponseException(s"SAML provider $provName not found"))

      val resp = parseSamlResp(samlResponse)
      val assertion = resp.getAssertions.get(0)
      val sign = assertion.getSignature

      val issueInstantStr = ISODateTimeFormat.dateTime().print(assertion.getIssueInstant)
      val issueInstant = ZonedDateTime.parse(issueInstantStr)
      val now = ZonedDateTime.now(clock)
      if (now.isBefore(issueInstant))
        throw new SamlResponseException("Assertion from future")
      val oldestPossibleInstant = now.minusSeconds(settings.samlAssertionLifetime.toSeconds)
      if (oldestPossibleInstant.isAfter(issueInstant))
        throw new SamlResponseException("Assertion is too old")

      assertion.getIssueInstant
      val cred = openSamlCredential(prov)
      val signValidator = new SignatureValidator(cred)
      try {
        signValidator.validate(sign)
      }
      catch {
        case e: Exception => throw new SamlResponseException("Failed to validate assertion signature", e)
      }

      val nameId = assertion.getSubject.getNameID.getValue
      Async.await(userQueryService.getByExternalId(prov.organizationId, nameId)).
        fold[Either[SamlUserData, UserEntity]](Left(SamlUserData(nameId, prov)))(ue => Right(ue))
    }
  }

  private def validateSamlProviderName(name: String, previousName: Option[String] = None): Future[_] = {
    Async.async {
      if (name.length < 1)
        throw new ValidationException(s"SamlProvider name should be at least 1 character long")
      if (!previousName.contains(name))
        if (Async.await(qService.getByName(rootOrgId, name)).isDefined)
          throw new ValidationException(s"SamlProvider with name $name already exists")
    }
  }

  private def generateRequestId(): String = UUID.randomUUID().toString

  private def parseSamlResp(resp64: String): Response = {
    val respBin = Base64.decodeBase64(resp64)
    val respStream = new ByteArrayInputStream(respBin)
    val dbf = DocumentBuilderFactory.newInstance()
    dbf.setNamespaceAware(true)
    val docBuilder = dbf.newDocumentBuilder()

    val doc = docBuilder.parse(respStream)
    val el = doc.getDocumentElement

    val unmarshaller = org.opensaml.xml.Configuration.getUnmarshallerFactory.getUnmarshaller(el)
    val resp = unmarshaller.unmarshall(el).asInstanceOf[Response]
    resp
  }

  private def openSamlCredential(samlProvider: SamlProvider): Credential = {
    val certStr = samlProvider.idProviderCertificate.split("\n").map(_.trim).filterNot(_.startsWith("--")).mkString
    val certBytes = Base64.decodeBase64(certStr)
    val keySpec = new X509EncodedKeySpec(certBytes);
    val certFactory = CertificateFactory.getInstance("X.509")
    val cert = certFactory.generateCertificate(new ByteArrayInputStream(certBytes))
    val cred = new BasicCredential()
    cred.setPublicKey(cert.getPublicKey)
    cred
  }

  private def samlResultHandlingUrl(provId: String): String = {
    s"${settings.urlWithPath}/saml/$provId/handle"
  }

  private def compress(in: String): Array[Byte] = {
    val inData = in.getBytes("UTF-8");
    val buf = new Array[Byte](inData.length + 20)
    val compresser = new Deflater(Deflater.DEFLATED, true);
    compresser.setInput(inData);
    compresser.finish();
    val len = compresser.deflate(buf);
    compresser.end();
    buf.take(len)
  }

  override protected def EntityName: String = "SamlProvider"
}

object SamlProviderService {
  case class SamlUserData(nameId: String, samlProvider: SamlProvider)
}
