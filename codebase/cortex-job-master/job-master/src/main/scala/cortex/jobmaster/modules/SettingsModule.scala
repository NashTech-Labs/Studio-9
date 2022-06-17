package cortex.jobmaster.modules

import com.typesafe.config.{ Config, ConfigFactory }
import cortex.common.config.ConfigFactoryExt
import cortex.jobmaster.jobs.job.dataset.DatasetTransferConfig
import cortex.jobmaster.jobs.job.DataAugmentationJobConfig
import cortex.jobmaster.jobs.job.analyse_csv.AnalyseCSVJobConfig
import cortex.jobmaster.jobs.job.columns_statistics.ColumnStatisticsJobConfig
import cortex.jobmaster.jobs.job.computer_vision.{
  AutoencoderJobConfig,
  ClassificationJobConfig,
  CustomModelJobConfig,
  LocalizationJobConfig,
  ModelImportJobConfig
}
import cortex.jobmaster.jobs.job.copier.CopierJobConfig
import cortex.jobmaster.jobs.job.cross_validation.CrossValidationJobConfig
import cortex.jobmaster.jobs.job.dremio_exporter.DremioExporterJobConfig
import cortex.jobmaster.jobs.job.dremio_importer.DremioImporterJobConfig
import cortex.jobmaster.jobs.job.image_uploading.ImageUploadingConfig
import cortex.jobmaster.jobs.job.pipeline_runner.PipelineRunnerJobConfig
import cortex.jobmaster.jobs.job.project_packager.ProjectPackagerJobConfig
import cortex.jobmaster.jobs.job.redshift_exporter.RedshiftExporterJobConfig
import cortex.jobmaster.jobs.job.redshift_importer.RedshiftImporterJobConfig
import cortex.jobmaster.jobs.job.splitter.SplitterJobConfig
import cortex.jobmaster.jobs.job.tabular.{ TabularJobConfig, TabularModelImportJobConfig }
import cortex.jobmaster.jobs.job.video_uploading.VideoUploadingJobConfig
import cortex.jobmaster.modules.SettingsModule._
import cortex.jobmaster.orion.service.domain.online_prediction.OnlinePredictionJobConfig

trait SettingsModule {

  ConfigFactoryExt.enableEnvOverride()

  private lazy val baseConfig: Config = ConfigFactory.load()

  lazy val akkaConfig: Config = baseConfig.getConfig("akka")

  lazy val heartbeatInterval: Int = baseConfig.getConfig("cortex-service").getInt("heartbeat-interval")

  lazy val baileUrl: String = baseConfig.getString("baile-url")
  lazy val sqlServerUrl: String = baseConfig.getString("sql-server-url")

  lazy val jmConf: JobMasterConfig = JobMasterConfig(
    baseConfig.getConfig("cortex-service"),
    baseConfig.getConfig("cortex-service").getString("name"),
    baseConfig.getConfig("cortex-job-master-tasks").getString("version"),
    baseConfig.getConfig("cortex-job-master-tasks").getString("registry")
  )

  lazy val baseS3Config: S3Config = {
    val s3Params: Config = baseConfig.getConfig("s3-params")

    S3Config(
      accessKey  = s3Params.getString("access-key"),
      secretKey  = s3Params.getString("secret-key"),
      region     = s3Params.getString("region"),
      baseBucket = s3Params.getString("bucket")
    )
  }

  lazy val baseDremioConfig: DremioConfig = {
    val dremioParams: Config = baseConfig.getConfig("dremio-params")

    DremioConfig(
      url         = dremioParams.getString("url"),
      username    = dremioParams.getString("username"),
      password    = dremioParams.getString("password"),
      source      = dremioParams.getString("source"),
      namespace   = dremioParams.getString("namespace"),
      s3TablesDir = dremioParams.getString("s3-tables-dir")
    )
  }

  val jobsPath = "cortex-job-master/jobs"
  val taskRpcPath = "cortex-job-master/task-rpc"
  val modelsPath = "cortex-job-master/models"
  val tmpDirPath = "cortex-job-master/tmp"

  lazy val imageUploadingConfig = ImageUploadingConfig(
    baseConfig.getConfig("image-uploading-job").getInt("block-size"),
    baseConfig.getConfig("image-uploading-job").getDouble("additional-task-size"),
    baseConfig.getConfig("image-uploading-job").getDouble("max-task-mem-size"),
    baseConfig.getConfig("image-uploading-job").getInt("parallelization-factor"),
    baseConfig.getConfig("image-uploading-job").getInt("min-group-size"),
    baseConfig.getConfig("image-uploading-job").getDouble("image-max-size"),
    baseConfig.getConfig("image-uploading-job").getDouble("cpus")
  )

  lazy val videoUploadingConfig = VideoUploadingJobConfig(
    baseConfig.getConfig("video-uploading-job").getDouble("cpus"),
    baseConfig.getConfig("video-uploading-job").getDouble("task-memory-limit"),
    baseConfig.getConfig("video-uploading-job").getInt("block-size")
  )

  lazy val onlinePredictionConfig = OnlinePredictionJobConfig(
    baseConfig.getConfig("online-prediction-job").getInt("max-predictions-per-result-file")
  )

  lazy val autoencoderConfig = AutoencoderJobConfig(
    baseConfig.getConfig("autoencoder-job").getDouble("cpus"),
    baseConfig.getConfig("autoencoder-job").getDouble("task-memory-limit"),
    baseConfig.getConfig("autoencoder-job").getInt("gpus")
  )

  lazy val classificationConfig = ClassificationJobConfig(
    baseConfig.getConfig("classification-job").getDouble("cpus"),
    baseConfig.getConfig("classification-job").getDouble("task-memory-limit"),
    baseConfig.getConfig("classification-job").getInt("gpus")
  )

  lazy val localizationConfig = LocalizationJobConfig(
    baseConfig.getConfig("localization-job").getDouble("cpus"),
    baseConfig.getConfig("localization-job").getDouble("task-memory-limit"),
    baseConfig.getConfig("localization-job").getInt("gpus"),
    baseConfig.getConfig("localization-job").getInt("feature-extractor-task-gpus"),
    baseConfig.getConfig("localization-job").getDouble("compose-video-task-memory-limit")
  )

  lazy val customModelJobConfig = CustomModelJobConfig(
    baseConfig.getConfig("custom-model-job").getDouble("cpus"),
    baseConfig.getConfig("custom-model-job").getDouble("task-memory-limit"),
    baseConfig.getConfig("custom-model-job").getInt("gpus")
  )

  lazy val analyseCSVConfig = AnalyseCSVJobConfig(
    baseConfig.getConfig("analyse-csv-job").getDouble("cpus"),
    baseConfig.getConfig("analyse-csv-job").getDouble("task-memory-limit")
  )

  lazy val copierConfig = CopierJobConfig(
    baseConfig.getConfig("copier-job").getDouble("cpus"),
    baseConfig.getConfig("copier-job").getDouble("task-memory-limit")
  )

  lazy val crossValidationConfig = CrossValidationJobConfig(
    baseConfig.getConfig("cross-validation-job").getDouble("cpus"),
    baseConfig.getConfig("cross-validation-job").getDouble("task-memory-limit")
  )

  lazy val splitterConfig = SplitterJobConfig(
    baseConfig.getConfig("splitter-job").getDouble("cpus"),
    baseConfig.getConfig("splitter-job").getDouble("task-memory-limit")
  )

  lazy val tabularConfig = TabularJobConfig(
    baseConfig.getConfig("tabular-job").getDouble("cpus"),
    baseConfig.getConfig("tabular-job").getDouble("task-memory-limit"),
    baseConfig.getConfig("tabular-job").getInt("k-folds"),
    baseConfig.getConfig("tabular-job").getInt("num-hyper-param-samples")
  )

  lazy val redshiftImporterConfig = RedshiftImporterJobConfig(
    baseConfig.getConfig("redshift-importer-job").getDouble("cpus"),
    baseConfig.getConfig("redshift-importer-job").getDouble("task-memory-limit")
  )

  lazy val redshiftExporterConfig = RedshiftExporterJobConfig(
    baseConfig.getConfig("redshift-exporter-job").getDouble("cpus"),
    baseConfig.getConfig("redshift-exporter-job").getDouble("task-memory-limit")
  )

  lazy val dataAugmentationConfig = DataAugmentationJobConfig(
    baseConfig.getConfig("data-augmentation-job").getDouble("cpus"),
    baseConfig.getConfig("data-augmentation-job").getDouble("task-memory-limit")
  )

  lazy val modelImportConfig = ModelImportJobConfig(
    baseConfig.getConfig("model-import-job").getDouble("cpus"),
    baseConfig.getConfig("model-import-job").getDouble("task-memory-limit")
  )

  lazy val projectPackagerConfig = ProjectPackagerJobConfig(
    baseConfig.getConfig("project-packager-job").getDouble("cpus"),
    baseConfig.getConfig("project-packager-job").getDouble("task-memory-limit")
  )

  lazy val columnStatisticsConfig = ColumnStatisticsJobConfig(
    baseConfig.getConfig("column-statistics-job").getDouble("cpus"),
    baseConfig.getConfig("column-statistics-job").getDouble("task-memory-limit")
  )

  lazy val tabularModelImportConfig = TabularModelImportJobConfig(
    cpus            = baseConfig.getConfig("tabular-model-import-job").getDouble("cpus"),
    taskMemoryLimit = baseConfig.getConfig("tabular-model-import-job").getDouble("task-memory-limit")
  )

  lazy val pipelineRunnerConfig = PipelineRunnerJobConfig(
    cpus            = baseConfig.getConfig("pipeline-runner-job").getDouble("cpus"),
    taskMemoryLimit = baseConfig.getConfig("pipeline-runner-job").getDouble("task-memory-limit"),
    gpus            = baseConfig.getConfig("pipeline-runner-job").getInt("gpus")
  )

  lazy val dremioImporterConfig = DremioImporterJobConfig(
    baseConfig.getConfig("dremio-importer-job").getDouble("cpus"),
    baseConfig.getConfig("dremio-importer-job").getDouble("task-memory-limit")
  )

  lazy val dremioExporterConfig = DremioExporterJobConfig(
    baseConfig.getConfig("dremio-exporter-job").getDouble("cpus"),
    baseConfig.getConfig("dremio-exporter-job").getDouble("task-memory-limit"),
    baseConfig.getConfig("dremio-exporter-job").getInt("chunksize")
  )

  lazy val datasetTransferConfig = DatasetTransferConfig(
    baseConfig.getConfig("dataset-transfer-job").getInt("parallelization-factor"),
    baseConfig.getConfig("dataset-transfer-job").getInt("min-group-size"),
    baseConfig.getConfig("dataset-transfer-job").getDouble("image-max-size"),
    baseConfig.getConfig("dataset-transfer-job").getDouble("cpus"),
    baseConfig.getConfig("dataset-transfer-job").getDouble("memory")
  )

  lazy val baseRedshiftConfig: RedshiftConfig = {
    val redshiftParams: Config = baseConfig.getConfig("redshift-params")

    RedshiftConfig(
      host      = redshiftParams.getString("host"),
      port      = redshiftParams.getInt("port"),
      database  = redshiftParams.getString("database"),
      username  = redshiftParams.getString("username"),
      password  = redshiftParams.getString("password"),
      s3IAMRole = redshiftParams.getString("s3-iam-role")
    )
  }
}

object SettingsModule {

  def apply(): SettingsModule = new SettingsModule {}

  case class JobMasterConfig(
      conf:          Config,
      serviceName:   String,
      tasksVersion:  String,
      tasksRegistry: String
  )

  case class S3Config(
      accessKey:  String,
      secretKey:  String,
      region:     String,
      baseBucket: String
  )

  case class DremioConfig(
      url:         String,
      username:    String,
      password:    String,
      source:      String,
      namespace:   String,
      s3TablesDir: String
  )

  case class RedshiftConfig(
      host:      String,
      port:      Int,
      database:  String,
      username:  String,
      password:  String,
      s3IAMRole: String
  )
}
