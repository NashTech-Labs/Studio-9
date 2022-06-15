package cortex.jobmaster.jobs.job.redshift_exporter

case class RedshiftExporterJobConfig(
    cpus:            Double,
    taskMemoryLimit: Double
)
