package cortex.testkit

import cortex.rpc.TaskRPC

trait WithTaskRPC {

  def taskRPC: TaskRPC

}
