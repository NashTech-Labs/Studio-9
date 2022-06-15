package cortex.jobmaster.orion.service.io

trait Marshaller[T, A] {

  def marshall(value: T): A

}

trait Unmarshaller[T, A] {

  def unmarshall(value: A): T

}
