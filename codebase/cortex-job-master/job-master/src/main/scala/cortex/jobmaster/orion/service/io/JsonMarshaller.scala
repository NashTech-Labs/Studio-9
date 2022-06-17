package cortex.jobmaster.orion.service.io
import play.api.libs.json.{ Reads, Writes }
import play.api.libs.json.Json

class JsonMarshaller[A](implicit writes: Writes[A]) extends Marshaller[A, Array[Byte]] {
  override def marshall(value: A): Array[Byte] = {
    Json.toJson(value).toString().getBytes
  }
}

class JsonUnmarshaller[A](implicit reads: Reads[A]) extends Unmarshaller[A, Array[Byte]] {
  override def unmarshall(value: Array[Byte]): A = {
    Json.parse(value).as[A]
  }
}
