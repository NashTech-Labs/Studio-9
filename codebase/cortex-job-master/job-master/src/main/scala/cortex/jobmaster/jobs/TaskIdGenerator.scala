package cortex.jobmaster.jobs

import java.util.UUID

trait TaskIdGenerator {
  def genTaskId(jobId: String): String = {
    s"$jobId---${UUID.randomUUID().toString}"
  }
}
