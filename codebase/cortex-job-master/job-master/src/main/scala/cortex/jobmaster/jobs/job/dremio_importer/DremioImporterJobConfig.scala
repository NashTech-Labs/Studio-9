package cortex.jobmaster.jobs.job.dremio_importer

case class DremioImporterJobConfig(
    cpus:            Double,
    taskMemoryLimit: Double
)
