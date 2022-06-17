package aries.rest.common

import akka.http.scaladsl.unmarshalling.Unmarshaller
import aries.domain.service.job.JobStatus

/**
 * Created by anthony.difrancesco on 8/1/17.
 */

trait CustomUnmarshallers {
  implicit val jobStatusUnmarshaller: Unmarshaller[String, JobStatus] = Unmarshaller.strict[String, JobStatus] { value =>
    JobStatus.deserialize(value)
  }
}

object CustomUnmarshallers extends CustomUnmarshallers
