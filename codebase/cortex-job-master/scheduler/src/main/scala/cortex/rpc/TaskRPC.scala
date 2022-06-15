package cortex.rpc

/**
 * RPC between task and TaskScheduler.
 * Used for passing parameters to tasks as well as get results when tasks are complete.
 * Corresponds to rpc module inside docker container
 */
trait TaskRPC {

  // type of rpc. Ex,. hdfs
  def rpcType: String

  def basePath: String

  // parameters to dockerized task where rpc type and endpoints are defined
  def getRpcDockerParams(taskPath: String): Seq[String] =
    Seq(s"--rpc-type=$rpcType")

  // passing parameters to task. Ex., via hdfs or by creating RPC server
  def passParameters(taskPath: String, serializedParams: Array[Byte]): Unit

  // getting task results string once completed. Ex., by reading results from hdfs
  def getResults(taskPath: String): Array[Byte]
}
