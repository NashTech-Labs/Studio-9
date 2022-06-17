package orion.service.job

import java.util.UUID

import akka.testkit.TestActorRef
import com.typesafe.config.ConfigFactory
import mesosphere.marathon.client.model.v2.{ App, Container, Docker, Parameter }
import orion.common.service.MarathonClient.AppStatus
import orion.service.job.JobMasterAppWorker.{ CreateJobMasterApp, GetJobMasterAppStatus, GetJobMasterAppStatusResult, JobMasterAppCreated }
import orion.testkit.service.ServiceBaseSpec

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.concurrent.Future

class JobMasterAppWorkerUnitSpec extends ServiceBaseSpec {

  // Fixtures
  val jobId = UUID.randomUUID()
  val nonExistentJobId = UUID.randomUUID()

  val mockDockerImage = "deepcortex/cortex-job-master:0.0.2-167-gd469bad-SNAPSHOT"
  val mockForceDockerPull = false
  val mockMesosMasterAddress = "master.mesos:5050"
  val mockAppEnvironmentVariables = Map("RABBIT_HOSTS" -> "127.0.0.1", "RABBIT_USERNAME" -> "guest", "RABBIT_PASSWORD" -> "guest")
  val mockCpus = 1D
  val mockMemory = 1024D
  val mockInstances = 1

  val expectedApp = {
    val container = new Container()
    container.setType("DOCKER")

    val dockerInfo = new Docker()
    dockerInfo.setImage(mockDockerImage)
    dockerInfo.setNetwork("HOST")
    dockerInfo.setForcePullImage(mockForceDockerPull)

    val javaOpts = {
      val xmx = mockMemory.toInt
      val xms = xmx / 2
      new Parameter("env", s"JAVA_OPTS=-Xms${xms}m -Xmx${xmx}m")
    }

    val params = Seq(new Parameter("rm", "true"), new Parameter("net", "host"), javaOpts)
    dockerInfo.setParameters(params)
    container.setDocker(dockerInfo)

    val constraints = List(
      List("cluster", "UNLIKE", "gpu").asJava,
      List("cluster", "UNLIKE", "gpu-jupyter").asJava,
      List("cluster", "UNLIKE", "cpu-jupyter").asJava
    ).asJava

    val app = new App()
    app.setId(jobId.toString)
    app.setContainer(container)
    app.setArgs(Seq("service", "--job-id", jobId.toString, "--mesos-master", mockMesosMasterAddress))
    app.setEnv(mockAppEnvironmentVariables)
    app.setCpus(mockCpus)
    app.setMem(mockMemory)
    app.setInstances(mockInstances)
    app.setConstraints(constraints)
    app.setPorts(Seq(new Integer(0)))
    app
  }

  val appStatus = AppStatus.Running

  trait Scope extends ServiceScope {
    val service = TestActorRef(new JobMasterAppWorker with MarathonClientSupportTesting {
      override val settings = new JobMasterSettings(ConfigFactory.load()) {
        override val dockerImage = mockDockerImage
        override val forceDockerPull = mockForceDockerPull
        override val appEnvironmentVariables = mockAppEnvironmentVariables
        override val mesosMasterAddress = mockMesosMasterAddress
        override val cpus = mockCpus
        override val memory = mockMemory
        override val instances = mockInstances
      }
    })
  }

  "When receiving a CreateJobMasterApp msg, the JobMasterAppWorker" should {
    "make a call to the Marathon api to create a new JobMaster app and respond with a JobMasterAppCreated if no errors" in new Scope {
      // Mock MarathonClient call
      mockMarathonClient.createAppExpects(expectedApp).returning(Future.successful(expectedApp))

      // Send msg
      service ! CreateJobMasterApp(jobId)

      // Verify service response
      expectMsg(JobMasterAppCreated(expectedApp))
    }
    "make a call to the Marathon api to create a new JobMaster app and respond with a Failure msg if there are errors" in new Scope {
      // Mock MarathonClient call
      val marathonException = new Exception("BOOM!")
      mockMarathonClient.createAppExpects(expectedApp).returning(Future.failed(marathonException))

      // Send msg
      service ! CreateJobMasterApp(jobId)

      // Verify service response
      expectMsgFailure(marathonException)
    }
  }

  "When receiving a GetJobMasterAppStatus msg, the JobMasterAppWorker" should {
    "make a call to the Marathon api to get the JobMaster app status and respond with some app status if app exists and there're no errors" in new Scope {
      // Mock MarathonClient call
      mockMarathonClient.getAppStatusExpects(jobId.toString).returning(Future.successful(Some(appStatus)))

      // Send msg
      service ! GetJobMasterAppStatus(jobId)

      // Verify service response
      expectMsg(GetJobMasterAppStatusResult(Some(appStatus)))
    }
    "make a call to the Marathon api to get the JobMaster app status and respond with none if app does not exist and there're no errors" in new Scope {
      // Mock MarathonClient call
      mockMarathonClient.getAppStatusExpects(nonExistentJobId.toString).returning(Future.successful(None))

      // Send msg
      service ! GetJobMasterAppStatus(nonExistentJobId)

      // Verify service response
      expectMsg(GetJobMasterAppStatusResult(None))
    }
    "make a call to the Marathon api to get the JobMaster app status and respond with a Failure msg if there are errors" in new Scope {
      // Mock MarathonClient call
      val marathonException = new Exception("BOOM!")
      mockMarathonClient.getAppStatusExpects(jobId.toString).returning(Future.failed(marathonException))

      // Send msg
      service ! GetJobMasterAppStatus(jobId)

      // Verify service response
      expectMsgFailure(marathonException)
    }
  }

}
