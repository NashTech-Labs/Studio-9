package cortex

class CortexException(message: String, cause: Throwable)
  extends Exception(message, cause) {

  // scalastyle:off null
  def this(message: String) = this(message, null)

}
