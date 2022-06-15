package cortex.rest.common

import akka.http.scaladsl.unmarshalling.Unmarshaller
import cortex.domain.service.job.JobStatus

trait CustomUnmarshallers {
  implicit val jobStatusUnmarshaller: Unmarshaller[String, JobStatus] = Unmarshaller.strict[String, JobStatus] { value =>
    JobStatus.deserialize(value)
  }
}

object CustomUnmarshallers extends CustomUnmarshallers
