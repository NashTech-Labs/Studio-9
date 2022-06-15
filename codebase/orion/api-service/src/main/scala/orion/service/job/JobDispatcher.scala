package orion.service.job

import akka.actor.{ ActorRef, Props }
import cortex.api.job.message.JobMessage
import orion.common.json4s.OrionJson4sSupport
import orion.common.service.{ NamedActor, Service }
import orion.ipc.rabbitmq.{ MlJobTopology, RabbitMqRpcClientSupport }

object JobDispatcher extends NamedActor {
  val Name = "job-dispatcher"

  def props(jobSupervisorShardRegion: ActorRef): Props = {
    Props(new JobDispatcher(jobSupervisorShardRegion))
  }
}

class JobDispatcher(jobSupervisorShardRegion: ActorRef) extends Service with OrionJson4sSupport with RabbitMqRpcClientSupport {

  implicit val ec = context.dispatcher

  override def preStart(): Unit = {
    subscribe[JobMessage](MlJobTopology.NewJobQueue)
    subscribe[JobMessage](MlJobTopology.JobMasterOutQueue)
    subscribe[JobMessage](MlJobTopology.CancelJobQueue)
  }

  def receive: Receive = {
    case msg: JobMessage => {
      jobSupervisorShardRegion forward msg
    }
  }

}
