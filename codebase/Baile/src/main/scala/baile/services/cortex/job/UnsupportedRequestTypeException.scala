package baile.services.cortex.job

import com.trueaccord.scalapb.GeneratedMessage

case class UnsupportedRequestTypeException(request: GeneratedMessage) extends RuntimeException(
  s"Request of class ${ request.getClass.getCanonicalName } is not supported"
)
