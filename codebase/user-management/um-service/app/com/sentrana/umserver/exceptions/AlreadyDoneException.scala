package com.sentrana.umserver.exceptions

/**
 * Created by Paul Lysak on 19.04.16.
 */
class AlreadyDoneException(val message: String, val id: Option[String] = None) extends RuntimeException(message)
