package orion.ipc.rabbitmq.setup.builders

import com.rabbitmq.client.Channel

trait Builder {

  def build(channel: Channel): Unit

  def name(): String
}
