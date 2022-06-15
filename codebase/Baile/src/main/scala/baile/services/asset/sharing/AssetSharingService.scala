package baile.services.asset.sharing

import java.time.Instant
import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.asset.sharing.SharedResourceDao
import baile.dao.asset.sharing.SharedResourceDao._
import baile.daocommons.WithId
import baile.daocommons.filters.{ Filter, TrueFilter }
import baile.domain.asset.AssetType
import baile.domain.asset.sharing.SharedResource
import baile.domain.usermanagement.User
import baile.services.asset.sharing.AssetSharingService.AssetSharingServiceError
import baile.services.asset.sharing.AssetSharingService.AssetSharingServiceError._
import baile.services.asset.sharing.exception.UnableToSendMailException
import baile.services.common.EntityService
import baile.services.usermanagement.UmService
import baile.utils.MailService
import baile.utils.TryExtensions._
import cats.data.EitherT
import cats.implicits._
import com.typesafe.config.Config

import scala.concurrent.{ ExecutionContext, Future }
import scala.io.Source
import scala.util.Try

class AssetSharingService(
  config: Config,
  mailService: MailService,
  umService: UmService,
  val dao: SharedResourceDao
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends EntityService[SharedResource, AssetSharingServiceError] {

  import AssetSharingService._

  val forbiddenError: AssetSharingServiceError = AccessDenied

  override val notFoundError: AssetSharingServiceError = ResourceNotFound

  private val sharingNotificationInvitationTemplatePath: String =
    config.getString("sharing.notification-invitation-template")
  private val signUpBaseUrl: String = config.getString("sharing.signup-base-url")
  private val appUrl: String = config.getString("sharing.app-url")

  def create(
    name: Option[String],
    recipientId: Option[UUID],
    recipientEmail: Option[String],
    assetType: AssetType,
    assetId: String
  )(implicit user: User): Future[Either[AssetSharingServiceError, WithId[SharedResource]]] = {

    def generateName: String = "shared-resource-" + java.util.UUID.randomUUID().toString

    val dateTime = Instant.now()
    val sharedResourceName = name getOrElse generateName

    def loadRecipientInfo(): Future[Either[AssetSharingServiceError, (Option[UUID], String, Option[String])]] =
      (recipientId, recipientEmail) match {
        case (Some(rId), _) =>
          umService.getUser(rId).map(_.bimap(_ => RecipientNotFound, { recipient =>
            (Some(recipient.id), recipient.email, Some(recipient.username))
          }))
        case (_, Some(rEmail)) =>
          umService.findUsers(email = Some(rEmail), limit = 1).map { users =>
            users.headOption.map { user =>
              (Some(user.id): Option[UUID], user.email, Some(user.username): Option[String])
            }.getOrElse((None, rEmail, None)).asRight
          }
        case _ => Future.successful(RecipientIsNotSpecified.asLeft)
      }

    def createResource(recipientId: Option[UUID], recipientEmail: String): Future[WithId[SharedResource]] =
      dao.create { _ =>
        SharedResource(
          ownerId = user.id,
          name = Some(sharedResourceName),
          created = dateTime,
          updated = dateTime,
          recipientId = recipientId,
          recipientEmail = Some(recipientEmail),
          assetType = assetType,
          assetId = assetId
        )
      }

    val response = for {
      recipientInfo <- EitherT(loadRecipientInfo())
      (userId, userEMail, userName) = recipientInfo
      _ <- EitherT(ensureAssetNotAlreadyShared(
        assetId,
        assetType,
        userEMail
      ))
      _ <- EitherT.right[AssetSharingServiceError](
        sendSharingEmail(
          userName.getOrElse(userEMail),
          userEMail,
          assetType,
          sharedResourceName,
          isInvitationWithSignUp = userId.isEmpty
        ).toFuture
      )
      result <- EitherT.right[AssetSharingServiceError](createResource(userId, userEMail))
    } yield result

    response.value
  }

  def deleteSharesForAsset(id: String, assetType: AssetType)(implicit user: User): Future[Unit] = {
    dao.deleteMany(OwnerIdIs(user.id) && AssetIdIs(id) && AssetTypeIs(assetType)).map(_ => ())
  }

  def list(
    assetId: Option[String],
    assetType: Option[AssetType]
  )(implicit user: User): Future[Either[AssetSharingServiceError, (Seq[WithId[SharedResource]], Int)]] = {
    val filter = assetId.fold[Filter](TrueFilter)(AssetIdIs) && assetType.fold[Filter](TrueFilter)(AssetTypeIs)
    listAll(filter, Seq.empty).map(_.map(result => (result, result.length)))
  }

  def listAll(recipientId: UUID): Future[Seq[WithId[SharedResource]]] = {
    dao.listAll(RecipientIdIs(recipientId))
  }

  def listAll(recipientId: UUID, assetType: AssetType): Future[Seq[WithId[SharedResource]]] = {
    dao.listAll(RecipientIdIs(recipientId) && AssetTypeIs(assetType))
  }

  def updateSharesForNewUser(email: String, userId: UUID): Future[Int] = {
    dao.updateMany(RecipientEmailIs(email), _.copy(
      recipientId = Some(userId)
    ))
  }

  def getOwner(
    id: String
  )(implicit user: User): Future[Either[AssetSharingServiceError, User]] = {
    getUser(id, OwnerNotFound, isOwner = true)
  }

  def getRecipient(
    id: String
  )(implicit user: User): Future[Either[AssetSharingServiceError, User]] = {
    getUser(id, RecipientNotFound, isOwner = false)
  }

  override protected def ensureCanRead(
    sharedResource: WithId[SharedResource],
    user: User
  ): Future[Either[AssetSharingServiceError, Unit]] = Future.successful {
    if ((sharedResource.entity.ownerId == user.id) || sharedResource.entity.recipientId.exists(_ === user.id)) {
      ().asRight
    } else forbiddenError.asLeft
  }

  override protected def ensureCanDelete(
    sharedResource: WithId[SharedResource],
    user: User
  ): Future[Either[AssetSharingServiceError, Unit]] = Future.successful {
    if (sharedResource.entity.ownerId == user.id) ().asRight
    else forbiddenError.asLeft
  }

  override protected def prepareCanReadFilter(user: User): Future[Filter] =
    Future.successful(OwnerIdIs(user.id) || RecipientIdIs(user.id))

  private def sendSharingEmail(
    receiverName: String,
    receiverEmail: String,
    assetType: AssetType,
    assetName: String,
    isInvitationWithSignUp: Boolean
  )(implicit user: User): Try[Unit] = Try {
    val url = if (isInvitationWithSignUp) signUpBaseUrl + "?email=" + receiverEmail else appUrl
    val body = createMailBody(
      sharingNotificationInvitationTemplatePath,
      user.username,
      user.firstName,
      user.lastName,
      assetType.toString,
      assetName
    ).replace("#APP_URL", url)

    mailService.sendHtmlFormattedEmail(
      subject = s"You're invited to collaborate on $assetType $assetName",
      messageBody = body,
      toAddress = receiverEmail,
      receiverName = receiverName
    ) recover {
      case ex => throw UnableToSendMailException(receiverEmail, ex)
    }
  }

  private def getUser(
    id: String,
    error: AssetSharingServiceError,
    isOwner: Boolean
  )(implicit user: User): Future[Either[AssetSharingServiceError, User]] = {

    def getUUID(sharedResource: WithId[SharedResource]): Option[UUID] = {
      if (isOwner) Some(sharedResource.entity.ownerId) else sharedResource.entity.recipientId
    }

    val result = for {
      resource <- EitherT(get(id)(user))
      id <- EitherT.fromOption[Future](getUUID(resource), RecipientNotFound)
      user <- EitherT(umService.getUser(id)).leftMap[AssetSharingServiceError](_ => error)
    } yield user

    result.value
  }

  private def createMailBody(
    templateResourceName: String,
    senderUserName: String,
    senderFirstName: String,
    senderLastName: String,
    assetType: String,
    assetName: String
  ): String = {
    Source.fromResource(templateResourceName).mkString
      .replace("#SENDER_USERNAME", senderUserName)
      .replace("#SENDER_FIRSTNAME", senderFirstName)
      .replace("#SENDER_LASTNAME", senderLastName)
      .replace("#ASSET_TYPE", assetType)
      .replace("#ASSET", assetName)
  }

  private def ensureAssetNotAlreadyShared(
    assetId: String,
    assetType: AssetType,
    recipientEmail: String
  ): Future[Either[AssetSharingServiceError, Unit]] = {
    dao.get(AssetIdIs(assetId) && AssetTypeIs(assetType) && RecipientEmailIs(recipientEmail)).map { resource =>
      if (resource.isDefined) {
        AlreadyShared.asLeft
      } else {
        ().asRight
      }
    }
  }

}

object AssetSharingService {

  sealed trait AssetSharingServiceError

  object AssetSharingServiceError {

    case object ResourceNotFound extends AssetSharingServiceError

    case object AccessDenied extends AssetSharingServiceError

    case object RecipientNotFound extends AssetSharingServiceError

    case object RecipientIsNotSpecified extends AssetSharingServiceError

    case object OwnerNotFound extends AssetSharingServiceError

    case object AlreadyShared extends AssetSharingServiceError

  }

}
