package com.sentrana.umserver.controllers

import javax.inject.{ Inject, Singleton }
import com.sentrana.umserver.dtos.{ ActivationResponse, CreateUserRequest, ErrorResponse, UserDeactivationRequest }
import com.sentrana.umserver.entities.UserEntity
import com.sentrana.umserver.exceptions.{ AlreadyDoneException, ValidationException }
import com.sentrana.umserver.services._
import com.sentrana.umserver.shared.dtos.enums.WellKnownPermissions
import com.sentrana.umserver.shared.dtos._
import org.slf4j.LoggerFactory
import play.api.http.MimeTypes
import play.api.libs.json._
import play.api.mvc.{ Action, AnyContent, Result }

import scala.async.Async
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Paul Lysak on 11.04.16.
 */
@Singleton
class UserController @Inject() (
    authenticationService:         AuthenticationService,
    userCommandService:            UserService,
    userQueryService:              UserQueryService,
    appQueryService:               ApplicationInfoQueryService,
    val securedControllerServices: SecuredControllerServices,
    userConverter:                 UserConverter,
    dataFilterInfoProcessor:       DataFilterInfoProcessor
) extends EntityMTCrudController[CreateUserRequest, UpdateUserAdminRequest, UserEntity] {
  import com.sentrana.umserver.JsonFormats._

  override def entityCrudService = userCommandService

  override def entityQueryService = userQueryService

  override protected implicit def createReqReads: Reads[CreateUserRequest] = CreateUserRequest.reads

  override protected implicit def updReqReads: Reads[UpdateUserAdminRequest] = Json.reads[UpdateUserAdminRequest]

  override protected def entityToDtoJson(entity: UserEntity): JsValue = Json.toJson(userConverter.toUserDto(entity))

  override protected def entityToDetailDtoJson(entity: UserEntity, queryString: Map[String, Seq[String]]): Future[JsValue] =
    userConverter.toUserDetailDto(entity, queryString).map(Json.toJson(_))

  override protected def permissionPrefix: String = "USERS"

  override protected def entityName: String = "user"

  override def create(orgId: String) = SecuredAction(RequirePermission(permissionPrefix + "_CREATE", Some(orgId))).async(parseObj[CreateUserRequest]()) { req =>
    log.info(s"Create $entityName: $req")

    def createUser = {
      if (req.body.requireEmailConfirmation.getOrElse(true)) {
        entityCrudService.createUserWithEmailActivation(orgId, req.body, req.user.username)
      }
      else {
        entityCrudService.create(orgId, req.body)
      }
    }
    Async.async {
      val user = Async.await(createUser)
      Ok(Async.await(entityToDetailDtoJson(user, req.queryString)))
    }
  }

  def signUp(orgId: String) = SecuredAction().async(parseObj[UserSignUpRequest]()) { req =>
    userCommandService.signUp(orgId, req.body, req.user.username).map { user =>
      GenericResponse(s"${entityName} signed up", Option(user.id)).ok
    }
  }

  def deactivateUser(orgId: String, id: String) = SecuredAction(RequirePermission(permissionPrefix + "_DEACTIVATE", Some(orgId))).async(parseObj[UserDeactivationRequest]()) { req =>
    log.info(s"Deactivated $entityName $id")
    Async.async {
      val user = Async.await(entityCrudService.deactivateUser(orgId, id, req.body))
      Ok(Async.await(entityToDetailDtoJson(user, req.queryString)))
    }
  }

  def activateUser(orgId: String, id: String) = SecuredAction(RequirePermission(permissionPrefix + "_ACTIVATE", Some(orgId))).async { req =>
    log.info(s"Activated $entityName $id")
    Async.async {
      val user = Async.await(entityCrudService.activateUser(orgId, id))
      Ok(Async.await(entityToDetailDtoJson(user, req.queryString)))
    }
  }

  def reSendActivationLink() = Action.async(parseObj[ReSendActivationLinkRequest]()) { req =>
    Async.async {
      Async.await(userCommandService.reSendActivationLink(req.body.email, req.body.appId)).fold(
        GenericResponse(s"No INACTIVE user with email ${req.body.email}").notFound
      )(user =>
        GenericResponse(s"Re-sent activation link to email ${req.body.email}").ok)
    }
  }

  def activate(orgId: String, userId: String, activationCode: String): Action[AnyContent] = Action.async { _ =>
    Async.async {
      Async.await(userCommandService.activate(orgId, userId, activationCode).map { u =>
        val message = s"User ${u.username} activated successfully"
        ActivationResponse(message).result
      }.recover {
        case _ =>
          ErrorResponse("The email confirmation link you followed is invalid").result
      })
    }
  }

  def userFilterInstances(orgId: String, userId: String) = SecuredAction(RequirePermissionEnum(WellKnownPermissions.USERS_GET_DETAILS, Option(orgId))).async {
    Async.async {
      val u = Async.await(userQueryService.getMandatory(orgId, userId))
      val f = Async.await(dataFilterInfoProcessor.getPreprocessedUserDataFilterInstances(u))
      Ok(Json.toJson(f))
    }
  }

  def updatePassword() = SecuredAction.async(parseObj[UpdatePasswordRequest]()) { req =>
    userCommandService.updatePassword(req.user.organizationId, req.user.id, req.body).map { user =>
      Ok("Password has been changed")
    }
  }

  def resetPassword() = Action.async(parseObj[PasswordResetRequest]()) { req =>
    Async.async {
      val user = Async.await(userQueryService.findActiveUser(req.body.email))
      Async.await(userCommandService.resetPassword(user, req.body.appId))
    }.recover {
      case e: Exception =>
        log.error(s"Error during password reset init. Will not be reported to client in order to avoid uncovering information which accounts are registered in the system", e)
        "ok"
    } map { _ =>
      Ok("Forgot password email was sent")
    }
  }

  def updateForgottenPassword() = Action.async(parseObj[UpdateForgottenPasswordRequest]()) { req =>
    Async.async {
      val user = Async.await(userQueryService.findActiveUser(req.body.email))
      val userWithUpdatedPassword = Async.await(userCommandService.updateForgottenPassword(user.id, req.body.secretCode, req.body.newPassword))
      Ok(entityToDtoJson(userWithUpdatedPassword))
    }
  }

  private val log = LoggerFactory.getLogger(classOf[UserController])
}
