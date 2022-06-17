package cortex.rest.job

import java.util.UUID

import akka.http.scaladsl.model.headers.BasicHttpCredentials
import cortex.common.json4s.CortexJson4sSupport
import cortex.testkit.E2ESpec


class JobHttpEndpointE2ESpec extends E2ESpec with CortexJson4sSupport with BaseJobE2EUtils {
  val cortexSettings = CortexSettings(system)

  override val cortexBaseUrl: String = cortexSettings.baseUrl
  override val credentials: BasicHttpCredentials = {
    BasicHttpCredentials(cortexSettings.credentials.username, cortexSettings.credentials.password)
  }

  trait Scope {
    val jobId = UUID.randomUUID()
    val ownerId: UUID = UUID.randomUUID()
    val basePathPrefix = "e2e_cortex"
  }

  //use "ssh -L 9000:cortex-api-rest.marathon.l4lb.thisdcos.directory:9000 -N centos@10.15.160.185"
  //to forward production cortex-api-rest service locally

  "Image uploading" should {

    "create image uploading job, execute it and respond with its result" in new Scope {
      val inputPath = s"$basePathPrefix/image_uploading.dat"
      val jobType = "IMAGE UPLOAD"
      submitJobAndAwaitCompletion(jobId, ownerId, inputPath, jobType)
    }
  }

  "Tabular" should {

    "create tabular train job, execute it and respond with its result" in new Scope {
      val inputPath = s"$basePathPrefix/tabular_train.dat"
      val jobType = "TRAIN"
      submitJobAndAwaitCompletion(jobId, ownerId, inputPath, jobType)
    }

    "create tabular predict job, execute it and respond with its result" in new Scope {
      val inputPath = s"$basePathPrefix/tabular_predict.dat"
      val jobType = "PREDICT"
      submitJobAndAwaitCompletion(jobId, ownerId, inputPath, jobType)
    }
  }

  "Computer vision" should {

    "create feature extractor train job, execute it and respond with its result" in new Scope {
      val inputPath = s"$basePathPrefix/fe_train.dat"
      val jobType = "TRAIN"
      submitJobAndAwaitCompletion(jobId, ownerId, inputPath, jobType)
    }

    "create CV transfer learning train job, execute it and respond with its result" in new Scope {
      val inputPath = s"$basePathPrefix/cv_train.dat"
      val jobType = "TRAIN"
      submitJobAndAwaitCompletion(jobId, ownerId, inputPath, jobType)
    }

    "create CV transfer learning predict job, execute it and respond with its result" in new Scope {
      val inputPath = s"$basePathPrefix/cv_predict.dat"
      val jobType = "PREDICT"
      submitJobAndAwaitCompletion(jobId, ownerId, inputPath, jobType)
    }

    "create CV full transfer learning job, execute it and respond with its result" in new Scope {
      val inputPath = s"$basePathPrefix/cv_full.dat"
      val jobType = "TRAIN"
      submitJobAndAwaitCompletion(jobId, ownerId, inputPath, jobType)
    }
  }
}
