package cortex.jobmaster.jobs.job.dremio_exporter

case class DremioExporterJobConfig(
    cpus:            Double,
    taskMemoryLimit: Double,
    chunksize:       Int
)
