package cortex.rpc
import cortex.TaskParams

/**
 * Used only for testing purposes to avoid real system dependencies
 */
class DummyTaskRPC extends TaskRPC {
  override val rpcType: String = "dummy-rpc"

  override def passParameters(taskPath: String, serializedParams: Array[Byte]): Unit = ()

  override def getResults(taskPath: String): Array[Byte] = Array.empty[Byte]

  override def basePath: String = ""
}
