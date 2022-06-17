package com.sentrana.umserver.exceptions

/**
 * Created by Alexander on 22.07.2016.
 */
class InvalidPasswordLengthException(username: String, minLength: Int, maxLength: Int)
  extends ValidationException(s"Invalid passwords length for user $username. Should be between $minLength and $maxLength")

class OwaspPasswordFormatException(username: String)
  extends ValidationException(
    """Password must contain at least 3 out of 4 character types: uppercase letter, lowercase letter, special character, digit""".stripMargin
  )

class PreviouslyUsedPasswordException(username: String) extends ValidationException(s"Password previously used for user $username")

class EmptyPasswordException(username: String) extends ValidationException(s"Empty password for user $username")
