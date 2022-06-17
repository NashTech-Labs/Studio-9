package cortex.jobmaster.jobs.job.redshift_importer

case class RedshiftImporterJobConfig(
    cpus:            Double,
    taskMemoryLimit: Double
)
