package com.sentrana.umserver.exceptions

/**
 * Created by Alexander on 31.08.2016.
 */
class TooManyRequestsException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)
