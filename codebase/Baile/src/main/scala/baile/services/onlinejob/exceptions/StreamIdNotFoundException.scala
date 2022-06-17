package baile.services.onlinejob.exceptions

case class StreamIdNotFoundException() extends RuntimeException(
  "Preconfigured stream id not found for online job"
)
