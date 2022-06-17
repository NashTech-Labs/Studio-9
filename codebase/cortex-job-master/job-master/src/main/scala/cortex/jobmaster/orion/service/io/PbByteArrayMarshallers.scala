package cortex.jobmaster.orion.service.io

import com.trueaccord.scalapb.{ GeneratedMessage, GeneratedMessageCompanion, Message }

/**
 * Serialize/deserialize job parameters and results for protobuf messages
 */

class PbByteArrayMarshaller[A <: GeneratedMessage] extends Marshaller[A, Array[Byte]] {
  override def marshall(value: A): Array[Byte] = {
    value.toByteArray
  }
}

class PbByteArrayUnmarshaller[A <: GeneratedMessage with Message[A]](implicit gmp: GeneratedMessageCompanion[A])
  extends Unmarshaller[A, Array[Byte]] {

  override def unmarshall(value: Array[Byte]): A = {
    gmp.parseFrom(value)
  }
}
