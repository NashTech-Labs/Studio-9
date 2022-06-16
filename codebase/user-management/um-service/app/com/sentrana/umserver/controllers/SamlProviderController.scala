package com.sentrana.umserver.controllers

import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.dtos._
import com.sentrana.umserver.entities.UserEntity
import com.sentrana.umserver.exceptions.{ SamlResponseException, ValidationException }
import com.sentrana.umserver.services._
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.data.validation.Constraints
import play.api.data.{ Form, Forms }
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json.{ JsValue, Json, Reads }
import play.api.mvc.{ Action, Request }

import scala.async.Async
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by Paul Lysak on 18.05.16.
 */
@Singleton
class SamlProviderController @Inject() (
    samlProviderService:      SamlProviderService,
    samlProviderQueryService: SamlProviderQueryService,
    authenticationService:    AuthenticationService,
    //    protected val userGroupQueryService: UserGroupQueryService,
    //    protected val orgQueryService:       OrganizationQueryService,
    applicationInfoService: ApplicationInfoService,
    val messagesApi:        MessagesApi,
    //    implicit val mongoDbService:         MongoDbService,
    userQueryService:              UserQueryService,
    userService:                   UserService,
    val securedControllerServices: SecuredControllerServices
) extends EntityMTCrudController[CreateSamlProviderRequest, UpdateSamlProviderRequest, SamlProvider] with I18nSupport {
  import SamlProviderController._
  import com.sentrana.umserver.JsonFormats._

  override def entityCrudService = samlProviderService

  override def entityQueryService = samlProviderQueryService

  override protected def entityToDtoJson(entity: SamlProvider): JsValue = Json.toJson(entity)

  override protected def permissionPrefix: String = "SAML_PROVIDERS"

  override protected implicit def updReqReads: Reads[UpdateSamlProviderRequest] = UpdateSamlProviderRequest.reads

  override protected implicit def createReqReads: Reads[CreateSamlProviderRequest] = CreateSamlProviderRequest.reads

  override protected def entityName: String = "SamlProvider"

  def getStarters(orgId: String, targetAppName: String) = Action.async {
    Async.async {
      val providers = Async.await(samlProviderService.list(orgId, limit = 10))
      val links = providers.map(sp => sp.name -> samlProviderService.buildStartLink(sp, targetAppName))
      Ok(Json.toJson(SamlStarter(links.toMap)))
    }
  }

  def responseHandler(provName: String) = Action.async(parse.urlFormEncoded(maxLength = 40000)) { implicit req =>
    Async.async {
      val samlResponse = getParamMandatory("SAMLResponse")
      val relayState = getParamMandatory("RelayState")
      val targetApp = Async.await(applicationInfoService.byName(relayState)).getOrElse(throw new SamlResponseException(s"Application not found: $relayState"))
      val targetUrl = targetApp.url.getOrElse(throw new SamlResponseException(s"URL not configured for app ${targetApp.name}"))

      log.debug(s"Received SAML response $samlResponse with relayState $relayState")
      val retrievedUserEither = Async.await(samlProviderService.getUserFromSamlResponse(provName, samlResponse, relayState))
      val form = samlSignUpForm.bindFromRequest()
      retrievedUserEither match {
        case Left(SamlProviderService.SamlUserData(nameId, _)) if form.hasErrors =>
          log.debug(s"SAML form binding for nameId $nameId failed, let's show it again: $req, $form")
          Ok(views.html.samlCaptureDetails(nameId, samlResponse, relayState, form))
        case Left(SamlProviderService.SamlUserData(nameId, prov)) =>
          log.info(s"SAML form binding for nameId $nameId successful, trying to create new user for provider ${prov.id} yet")
          val formData = form.get
          val existingUsers = Async.await(userQueryService.byUserName(prov.organizationId, formData.userName))
          if (existingUsers.nonEmpty) {
            log.info(s"SAML form processing for nameId $nameId: user ${formData.userName} already exists in org ${prov.organizationId}")
            Ok(views.html.samlCaptureDetails(nameId, samlResponse, relayState, form.withError("username", "User with such name already exists")))
          }
          else {
            log.info(s"SAML form processing for nameId $nameId: going to create user ${formData.userName} in org ${prov.organizationId}")
            val newUser = Async.await(createUser(nameId, formData, prov))
            val token = authenticationService.issueToken(newUser)
            Redirect(targetUrl, Map("access_token" -> Seq(token._1)))
          }
        case Right(user) =>
          log.info(s"SAML form processing for nameId ${user.externalId}: user exists, issuing token")
          val token = authenticationService.issueToken(user)
          Redirect(targetUrl, Map("access_token" -> Seq(token._1)))
      }
    }.recover({
      case e: SamlResponseException =>
        log.error(s"SAML response processing for provider $provName failed", e)
        BadRequest(views.html.samlResponseError(e.getMessage))
      case e: Exception =>
        BadRequest(s"SAML response handling failed: \n ${e.getMessage} \n ${e.getStackTrace.toSeq.map(_.toString).mkString("\n")}")
    })
  }

  def starterTestPage = Action.async { req =>
    val orgId = req.getQueryString("orgId").getOrElse("orgs_sentrana")
    val targetUrl = req.getQueryString("targetAppName").getOrElse("sampleApp1")

    Async.async {
      val providers = Async.await(samlProviderService.list(orgId))
      val links = providers.map(prov =>
        prov.name -> samlProviderService.buildStartLink(prov, targetUrl)).toMap

      Ok(views.html.samlTestPage(orgId, targetUrl, links))
    }
  }

  private def createUser(nameId: String, samlSignUp: SamlSignUp, prov: SamlProvider): Future[UserEntity] = {
    val createReq = CreateUserRequest(
      username   = samlSignUp.userName,
      email      = samlSignUp.email,
      password   = samlSignUp.password,
      firstName  = samlSignUp.firstName,
      lastName   = samlSignUp.lastName,
      groupIds   = prov.defaultGroupIds,
      externalId = Option(nameId)
    )
    userService.create(prov.organizationId, createReq)
  }

  private def getParam(name: String)(implicit req: Request[Map[String, Seq[String]]]): Option[String] =
    req.body.get(name).flatMap(_.headOption)

  private def getParamMandatory(name: String)(implicit req: Request[Map[String, Seq[String]]]): String =
    getParam(name).getOrElse(throw new ValidationException(s"Missing required parameter $name"))

  private val log = LoggerFactory.getLogger(classOf[SamlProviderController])
}

object SamlProviderController {
  import Forms._

  case class SamlSignUp(userName: String, firstName: String, lastName: String, email: String, password: String)

  private val samlSignUpForm = Form(
    Forms.mapping(
      "username" -> nonEmptyText(minLength = 1, maxLength = 128),
      "firstName" -> nonEmptyText(minLength = 1, maxLength = 128),
      "lastName" -> nonEmptyText(minLength = 1, maxLength = 128),
      "email" -> nonEmptyText(minLength = 1, maxLength = 128).verifying(Constraints.emailAddress),
      "password" -> nonEmptyText(minLength = 1, maxLength = 128)
    )(SamlSignUp.apply)(SamlSignUp.unapply)
  )
}

