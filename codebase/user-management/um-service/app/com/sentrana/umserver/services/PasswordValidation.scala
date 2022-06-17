package com.sentrana.umserver.services

import com.sentrana.umserver.UmSettings
import com.sentrana.umserver.entities.{ MongoFormats, UserEntity }
import com.sentrana.umserver.exceptions._
import com.sentrana.umserver.shared.dtos.enums.SortOrder
import com.sentrana.umserver.shared.dtos.{ UpdatePasswordRequest, UserPasswordHistory }
import com.sentrana.umserver.utils.{ MongoQueryUtil, PasswordHash }
import play.api.Configuration

import scala.concurrent.ExecutionContext.Implicits.global
import scala.annotation.tailrec
import scala.concurrent.Future

/**
 * Created by Alexander on 23.07.2016.
 */
trait PasswordValidation extends PasswordPreviouslyUsedSupport
    with IdenticalCharactersInARawSupport
    with OWASPPasswordComplexitySupport {

  private val identicalCharactersInARaw = 2

  def umSettings: UmSettings

  def mongoDbService: MongoDbService

  def isValidPasswordLength(password: String): Boolean = {
    password.length >= umSettings.passwordValidation.minLength &&
      password.length <= umSettings.passwordValidation.maxLength
  }

  def validateNewPassword(user: UserEntity, newPassword: String): Future[String] = {
    for {
      nonEmptyNewPassword <- validateIfPasswordIsEmpty(user, newPassword)
      newPasswordWithValidLength <- validatePasswordsLength(user, nonEmptyNewPassword)
      newPasswordValidatedAgainsIdenticalCharsInARaw <- validateIdenticalCharactersInARaw(user, newPasswordWithValidLength)
      newPasswordValidatedAgainstOwaspComplexity <- validateOwaspComplexity(user, newPasswordValidatedAgainsIdenticalCharsInARaw)
      validatedNewPassword <- validatePreviousUsage(user, newPasswordValidatedAgainstOwaspComplexity)
    } yield validatedNewPassword
  }

  def validateOldPassword(user: UserEntity, oldPassword: String): Future[String] = {
    if (!PasswordHash.parse(user.password).checkPassword(oldPassword))
      throw new AuthenticationException("Old password doesn't match")
    else
      Future.successful(oldPassword)
  }

  private def validateIfPasswordIsEmpty(user: UserEntity, newPassword: String): Future[String] = {
    if (newPassword.isEmpty)
      throw new EmptyPasswordException(user.username)
    else
      Future.successful(newPassword)
  }

  private def validatePasswordsLength(user: UserEntity, newPassword: String): Future[String] = {
    if (!isValidPasswordLength(newPassword))
      throw new InvalidPasswordLengthException(user.username, umSettings.passwordValidation.minLength, umSettings.passwordValidation.maxLength)
    else
      Future.successful(newPassword)
  }

  private def validateIdenticalCharactersInARaw(user: UserEntity, newPassword: String): Future[String] = {
    if (!lessIdenticalCharactersInARaw(newPassword, identicalCharactersInARaw))
      throw new OwaspPasswordFormatException(user.username)
    else
      Future.successful(newPassword)
  }

  private def validateOwaspComplexity(user: UserEntity, newPassword: String): Future[String] = {
    if (!isSatisfyOwaspComplexity(newPassword))
      throw new OwaspPasswordFormatException(user.username)
    else
      Future.successful(newPassword)
  }

  private def validatePreviousUsage(user: UserEntity, newPassword: String): Future[String] = {
    passwordPreviouslyUsed(user, newPassword).map { isPasswordPreviouslyUsed =>
      if (isPasswordPreviouslyUsed)
        throw new PreviouslyUsedPasswordException(user.username)
      else
        newPassword
    }
  }
}

sealed trait PasswordPreviouslyUsedSupport {
  import MongoFormats.userPasswordHistoryMongoFormat

  private val lastPreviousPasswords = 5

  def mongoDbService: MongoDbService

  def passwordPreviouslyUsed(user: UserEntity, newPassword: String): Future[Boolean] = {
    import org.mongodb.scala.model.Filters._
    val lastPasswordsF = mongoDbService.find[UserPasswordHistory](
      equal("userId", user.id),
      MongoQueryUtil.buildSortDocOpt(Map("created" -> SortOrder.ASC), Set("created")),
      limit = lastPreviousPasswords
    ).toFuture()

    lastPasswordsF.map { lastPasswords =>
      lastPasswords.exists(userPasswordHistory => PasswordHash.parse(userPasswordHistory.password).checkPassword(newPassword))
    }
  }
}

sealed trait IdenticalCharactersInARawSupport {

  def lessIdenticalCharactersInARaw(input: String, identicalCharsInARaw: Int): Boolean = {
    @tailrec
    def lessIdenticalCharactersInARawRec(current: Char, charsToVerify: String, count: Int): Boolean = {
      if (count >= identicalCharsInARaw) {
        false
      }
      else if (charsToVerify.isEmpty) {
        true
      }
      else {
        lessIdenticalCharactersInARawRec(
          charsToVerify.head,
          charsToVerify.tail,
          if (current == charsToVerify.head) count + 1 else 0
        )
      }
    }

    lessIdenticalCharactersInARawRec(input.head, input.tail, 0)
  }
}

sealed trait OWASPPasswordComplexitySupport {
  private val atLeastOneUpperCasePattern = "[A-Z]+".r
  private val atLeastOneLowerCasePattern = "[a-z]+".r
  private val atLeastOneDigitPattern = "\\d+".r
  private val atLeastOneSpecialPattern = "[\\W,\\s]+".r
  private val count: Int = 3

  private val complexityRules = Array(atLeastOneUpperCasePattern, atLeastOneLowerCasePattern, atLeastOneDigitPattern, atLeastOneSpecialPattern)

  def isSatisfyOwaspComplexity(password: String): Boolean = {
    complexityRules.collect {
      case regex if (regex.findAllIn(password).toArray.length > 0) => regex
    }.length >= count
  }
}
