package gemini.services.jupyter

import java.util.UUID

import akka.actor.{ ActorRef, ActorSystem }
import akka.stream.ActorMaterializer
import gemini.services.jupyter.JupyterMesosResourceProvider._
import resscheduler.ResourceProvider.RequestResourceError.AuthorizationError
import resscheduler.mesos.MesosResourceProvider

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

class JupyterMesosResourceProvider(
  cpusPerSlave: Int,
  memoryPerSlave: Long,
  maxMachines: Int,
  maxCpus: Int,
  maxGpus: Int,
  mesosFrameworksMonitor: ActorRef,
  actorAskTimeout: FiniteDuration,
  jupyterSessionService: JupyterSessionService
)(
  implicit actorSystem: ActorSystem,
  materializer: ActorMaterializer,
  executionContext: ExecutionContext
) extends MesosResourceProvider(
      cpusPerSlave,
      memoryPerSlave,
      maxMachines,
      maxCpus,
      maxGpus,
      mesosFrameworksMonitor,
      actorAskTimeout
    ) {

  override protected[jupyter] def authorize(token: String): Future[Either[AuthorizationError, Unit]] = {
    val (sessionId, sessionToken) = splitSessionIdAndToken(token)
    jupyterSessionService.authenticate(sessionId, sessionToken).map { isAuthenticated =>
      Either.cond(isAuthenticated, (), JupyterAuthorizationError)
    }
  }

  override protected def environment(token: String): Map[String, String] = Map.empty
}

object JupyterMesosResourceProvider {

  case object JupyterAuthorizationError extends AuthorizationError

  def combineSessionIdAndToken(sessionId: UUID, sessionToken: String): String = sessionId + "_" + sessionToken

  def splitSessionIdAndToken(authKey: String): (UUID, String) = {
    val parts = authKey.split("_")
    (UUID.fromString(parts(0)), parts(1))
  }
}
