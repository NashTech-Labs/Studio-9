package com.sentrana.umserver

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.sentrana.umserver.dtos.SamlProvider
import com.sentrana.umserver.shared.JsonFormatsShared
import com.sentrana.umserver.shared.dtos._
import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
 * Created by Paul Lysak on 13.04.16.
 */
object JsonFormats extends JsonFormatsShared {
  implicit val dataFilterInstanceWrites = Json.format[DataFilterInstance]

  implicit val organizationFormat = Json.format[Organization]

  implicit val userWrites = Json.writes[User]

  implicit val permissionFormat = Json.format[Permission]

  implicit val userGroupFormat = Json.format[UserGroup]

  implicit val samlProviderFormat = Json.format[SamlProvider]

  implicit val clientSecretInfoWrites = Json.writes[ClientSecretInfo]

  implicit val applicationInfoWrites = Json.writes[ApplicationInfo]

  implicit val dataFilterInfoSettingsFormat = Json.format[DataFilterInfoSettings]

  implicit val dataFilterInfoFormat = Json.format[DataFilterInfo]

  implicit val userSignUpRequestReads = Json.reads[UserSignUpRequest]

  implicit val updatePasswordRequestReads = Json.reads[UpdatePasswordRequest]

  implicit val updateForgottenPasswordRequestReads = Json.reads[UpdateForgottenPasswordRequest]

  implicit val reSendActivationLinkRequestReads = Json.reads[ReSendActivationLinkRequest]

  implicit val userPasswordHistoryFormat = Json.format[UserPasswordHistory]

  implicit val passwordResetRequestReads = Json.format[PasswordResetRequest]

  implicit def seqPartContainerWrites[T](implicit dw: Writes[T]): Writes[SeqPartContainer[T]] = (
    (JsPath \ "data").write[List[T]].contramap[Seq[T]](_.toList) and
    (JsPath \ "offset").write[Long] and
    (JsPath \ "total").write[Long]
  )(unlift(SeqPartContainer.unapply[T]))
}
