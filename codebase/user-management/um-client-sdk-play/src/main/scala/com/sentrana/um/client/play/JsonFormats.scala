package com.sentrana.um.client.play

import com.sentrana.umserver.shared.JsonFormatsShared
import com.sentrana.umserver.shared.dtos._
import play.api.libs.json.{ JsPath, Reads, Json }
import play.api.libs.functional.syntax._

/**
 * Created by Paul Lysak on 26.04.16.
 */
object JsonFormats extends JsonFormatsShared {
  implicit val dataFilterInstanceReads = Json.reads[DataFilterInstance]

  implicit val orgReads = Json.reads[Organization]

  implicit val userReads = Json.reads[User]

  implicit val permissionsReads = Json.reads[Permission]

  implicit val userGroupReads = Json.reads[UserGroup]

  implicit val dataFilterInfoSettingsReads = Json.reads[DataFilterInfoSettings]

  implicit val dataFilterInfoReads = Json.reads[DataFilterInfo]

  implicit val userSignUpRequestWrites = Json.writes[UserSignUpRequest]

  implicit val reSendActivationLinkRequestWrites = Json.writes[ReSendActivationLinkRequest]

  implicit val passwordResetRequestWrites = Json.writes[PasswordResetRequest]

  implicit val updatePasswordRequestWrites = Json.writes[UpdatePasswordRequest]

  implicit val updateForgottenPasswordRequestWrites = Json.writes[UpdateForgottenPasswordRequest]

  implicit def seqPartContainerReads[T](implicit dr: Reads[T]): Reads[SeqPartContainer[T]] = (
    (JsPath \ "data").read[Seq[T]] and
    (JsPath \ "offset").read[Long] and
    (JsPath \ "total").read[Long]
  )(SeqPartContainer.apply[T] _)

}

