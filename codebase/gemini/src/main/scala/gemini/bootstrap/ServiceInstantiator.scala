// $COVERAGE-OFF$
package gemini.bootstrap

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings }
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import gemini.services.info.InfoService
import gemini.services.jupyter._
import gemini.utils.DurationExtensions._
import mesosphere.marathon.client.MarathonClient
import resscheduler.ResourceProvider
import resscheduler.mesos.{ MesosFrameworksMonitor, MesosService }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class ServiceInstantiator(conf: Config)(
  implicit system: ActorSystem,
  val logger: LoggingAdapter,
  materializer: ActorMaterializer
) {

  implicit val ec: ExecutionContext = system.dispatcher

  lazy val infoService: InfoService = new InfoService

  lazy val jupyterSessionService: JupyterSessionService = new JupyterSessionService(
    jupyterSessionSupervisorShardRegion,
    pubSubMediator,
    1.minute
  )

  lazy val resourceProvider: ResourceProvider[Future] = {
    val resourceSchedulerConfig = conf.getConfig("resource-scheduler")
    val mesosService = new MesosService(
      Http(),
      MesosService.Settings(conf.getString("mesos.master"))
    )
    val mesosFrameworksMonitor: ActorRef = system.actorOf(
      Props(
        new MesosFrameworksMonitor(
          mesosService = mesosService,
          imageName = dockerImage,
          resourcesAllocationTimeout = resourceSchedulerConfig.getDuration("resources-allocation-timeout").toScala,
          tasksLaunchTimeout = resourceSchedulerConfig.getDuration("tasks-launch-timeout").toScala
        )
      )
    )
    new JupyterMesosResourceProvider(
      cpusPerSlave = resourceSchedulerConfig.getInt("cpus-per-slave"),
      memoryPerSlave = resourceSchedulerConfig.getInt("memory-per-slave"),
      maxMachines = resourceSchedulerConfig.getInt("max-machines"),
      maxCpus = resourceSchedulerConfig.getInt("max-cpus"),
      maxGpus = resourceSchedulerConfig.getInt("max-gpus"),
      mesosFrameworksMonitor = mesosFrameworksMonitor,
      actorAskTimeout = resourceSchedulerConfig.getDuration("actor-ask-timeout").toScala,
      jupyterSessionService = jupyterSessionService
    )
  }

  private lazy val pubSubMediator = DistributedPubSub(system).mediator

  private lazy val marathon = MarathonClient.getInstance(conf.getString("marathon-client.url"))

  private lazy val taskKillGracePeriod = conf.getDuration("jupyter-lab.application.task-kill-grace-period").toScala

  private lazy val jupyterAppConfig = conf.getConfig("jupyter-lab.application")
  private lazy val dockerImage = jupyterAppConfig.getString("docker-image")

  private lazy val jupyterAppService = {
    val geminiHostname = conf.getString("hostname")
    val geminiPort = conf.getInt("http.port")
    new JupyterAppService(
      settings = JupyterAppService.Settings(
        dockerImage = dockerImage,
        forcePullImage = jupyterAppConfig.getBoolean("force-pull-image"),
        defaultMemory = jupyterAppConfig.getDouble("default-memory"),
        defaultNumberOfCPUs = jupyterAppConfig.getDouble("default-number-of-cpus"),
        defaultNumberOfGPUs = jupyterAppConfig.getDouble("default-number-of-gpus"),
        baseJupyterLabDomain = conf.getString("jupyter-lab.url.base-domain"),
        heartbeatInterval = jupyterAppConfig.getDuration("heartbeat-interval").toScala,
        projectFilesSyncInterval = jupyterAppConfig.getDuration("project-files-sync-interval").toScala,
        geminiHostname = geminiHostname,
        geminiPort = geminiPort,
        taskKillGracePeriod = taskKillGracePeriod,
        deepcortexUrl = conf.getString("deepcortex-url"),
        sqlServerUrl = conf.getString("sql-server-url"),
        resourceSchedulerUrl = s"http://$geminiHostname:$geminiPort/${conf.getString("http.prefix")}"
      ),
      marathon = marathon
    )
  }

  private lazy val jupyterSessionSupervisorSharding = new JupyterSessionSupervisorSharding(
    numberOfShards = conf.getInt("supervisor-shard-count")
  )

  private lazy val jupyterSessionSupervisorShardRegion: ActorRef = {
    val settings = JupyterSessionSupervisor.Settings(
      stateTimeout = 15.minute,
      pollingPeriod = 5.seconds,
      endTimeout = 5.minutes,
      baseJupyterLabDomain = conf.getString("jupyter-lab.url.base-domain"),
      useHttpsForUrl = conf.getBoolean("jupyter-lab.url.use-https"),
      taskKillGracePeriod = taskKillGracePeriod,
      taskResourcesWaitPeriod = conf.getDuration("jupyter-lab.application.task-resources-wait-period").toScala
    )
    ClusterSharding(system).start(
      typeName = JupyterSessionSupervisor.name,
      entityProps = JupyterSessionSupervisor.props(settings, pubSubMediator, jupyterAppService),
      settings = ClusterShardingSettings(system),
      extractEntityId = jupyterSessionSupervisorSharding.extractEntityId,
      extractShardId = jupyterSessionSupervisorSharding.extractShardId
    )
  }
}
