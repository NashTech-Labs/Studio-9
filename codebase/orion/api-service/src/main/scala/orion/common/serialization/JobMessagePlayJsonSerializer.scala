package orion.common.serialization

import cortex.api.job.message.JobMessage

class JobMessagePlayJsonSerializer extends PlayJsonSerializer(classOf[JobMessage], JobMessage.format)
