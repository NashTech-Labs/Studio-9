package cortex.api


trait RMQBaseMessage {

  // this id is just needed for making
  // its successors compatible with orion-ipc rmq client
  val id: String
}
