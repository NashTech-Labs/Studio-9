package baile.bootstrap

import akka.actor.{ ActorRef, ActorSystem, PoisonPill, Props, Scheduler }
import akka.cluster.singleton._
import akka.event.LoggingAdapter
import akka.http.caching.LfuCache
import akka.http.caching.scaladsl.{ CachingSettings, LfuCacheSettings }
import akka.http.scaladsl.{ Http, HttpExt }
import akka.stream.ActorMaterializer
import akka.util.Timeout
import baile.dao.cv.model.CVModelDao
import baile.dao.cv.prediction.CVPredictionDao
import baile.dao.experiment.ExperimentDao
import baile.dao.onlinejob.OnlineJobDao
import baile.dao.pipeline.{ PipelineDao, PipelineOperatorDao }
import baile.dao.table.{ RedshiftTableDataDao, TableDataDao }
import baile.dao.tabular.prediction.TabularPredictionDao
import baile.daocommons.EntityDao
import baile.domain.asset.{ Asset, AssetType }
import baile.domain.common.S3Bucket
import baile.services.argo.ArgoService
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.{ AuthenticationService, FileUploadService, MLEntityExportImportService, S3BucketService }
import baile.services.cortex.job.{ AriesService, CortexJobService, CortexService, JobMetaService }
import baile.services.cv.CVTLModelPrimitiveService
import baile.services.cv.model._
import baile.services.cv.online.CVOnlinePredictionService
import baile.services.cv.prediction.{ CVPredictionNestedUsage, CVPredictionService, CVPredictionSharedAccess }
import baile.services.dataset.DatasetService
import baile.services.dcproject.SessionService.SessionNodeParams
import baile.services.dcproject.{ DCProjectPackageService, DCProjectService, SessionMonitor, SessionService }
import baile.services.experiment.{
  ExperimentAggregatedAsset,
  ExperimentCommonService,
  ExperimentDelegator,
  ExperimentService
}
import baile.services.gemini.GeminiService
import baile.services.images._
import baile.services.onlinejob.{ OnlineJobService, OnlineJobSharedAccess, OnlinePredictionConfigurator }
import baile.services.pipeline.category.CategoryService
import baile.services.pipeline.{ PipelineOperatorService, PipelineService, PipelineSharedAccess, _ }
import baile.services.process.{ ProcessMonitor, ProcessService }
import baile.services.project.ProjectService
import baile.services.remotestorage.S3StorageService
import baile.services.table.TableService
import baile.services.tabular.model._
import baile.services.tabular.prediction._
import baile.services.usermanagement.datacontract.UserResponse
import baile.services.usermanagement.{ OwnershipTransferRegistry, UMSentranaService, UmService }
import baile.utils.DurationConverter._
import baile.utils.MailService
import baile.utils.StringExtensions._
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

class ServiceInstantiator(
  val conf: Config,
  private val defaultValuesConfig: Config,
  daoInstantiator: DaoInstantiator
)(
  implicit system: ActorSystem,
  val logger: LoggingAdapter,
  materializer: ActorMaterializer
) { instantiator =>
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val scheduler: Scheduler = system.scheduler

  lazy val mailService: MailService = new MailService(conf, logger)
  lazy val umService: UmService = new UmService(
    OwnershipTransferRegistry,
    conf,
    sentranaServiceActor,
    mailService,
    logger
  )
  lazy val processService: ProcessService = new ProcessService(
    processMonitor,
    daoInstantiator.processDao
  )(ec, Timeout(processMonitorStartupRequestTimeout * 2), logger)
  lazy val authenticationService: AuthenticationService = new AuthenticationService(
    umService,
    processService
  )
  lazy val jobMetaService: JobMetaService = new JobMetaService(
    conf,
    cortexJobMetaStorage
  )
  lazy val s3BucketService: S3BucketService = new S3BucketService(conf)
  lazy val cortexService: CortexService = new CortexService(conf, http)
  lazy val ariesService: AriesService = new AriesService(conf, http)
  lazy val cortexJobService: CortexJobService = new CortexJobService(jobMetaService, cortexService, ariesService)
  lazy val imagesCommonService: ImagesCommonService = new ImagesCommonService(
    pictureDao = daoInstantiator.pictureDao,
    albumDao = daoInstantiator.albumDao,
    pictureStorage = pictureStorage,
    storagePathPrefix = conf.getString("album.storage-prefix"),
    imagesProcessingBatchSize = conf.getInt("album.images-job-result-processing.batch-size")
  )
  lazy val cvTLModelPrimitiveService: CVTLModelPrimitiveService = new CVTLModelPrimitiveService(
    daoInstantiator.cvTLModelPrimitiveDao,
    dcProjectPackageService
  )
  lazy val albumService: AlbumService = new AlbumService(
    imagesCommonService,
    daoInstantiator.albumDao,
    pictureStorage,
    cortexJobService,
    processService,
    assetSharingService,
    projectService
  ) with CVModelSharedAccess
    with CVPredictionSharedAccess
    with OnlineJobSharedAccess
    with PipelineSharedAccess
    with CVModelNestedUsage
    with CVPredictionNestedUsage
    with ExperimentAggregatedAsset
    with OwnershipTransferRegistry.Member {
    protected val experimentDao: ExperimentDao = daoInstantiator.experimentDao
    protected val cvModelDao: CVModelDao = daoInstantiator.cvModelDao
    protected val cvPredictionDao: CVPredictionDao = daoInstantiator.cvPredictionDao
    protected val onlineJobDao: OnlineJobDao = daoInstantiator.onlineJobDao
    protected val pipelineDao: PipelineDao = daoInstantiator.pipelineDao
    protected val pipelineOperatorDao: PipelineOperatorDao = daoInstantiator.pipelineOperatorDao
  }

  lazy val pictureService: PictureService = new PictureService(
    imagesCommonService,
    albumService,
    pictureStorage,
    fileUploadService,
    daoInstantiator.pictureDao
  )
  lazy val imageUploadService: ImagesUploadService = new ImagesUploadService(
    daoInstantiator.pictureDao,
    albumService,
    pictureService,
    imagesCommonService,
    cortexJobService,
    processService,
    s3BucketService,
    conf
  )

  lazy val defaultAugmentationValuesService =
    new DefaultAugmentationValuesService(defaultValuesConfig.getConfig("augmentation-default-values"))

  lazy val cvModelCommonService: CVModelCommonService = new CVModelCommonService(
    daoInstantiator.albumDao,
    daoInstantiator.cvModelDao,
    daoInstantiator.pictureDao,
    daoInstantiator.tableDao,
    albumService,
    imagesCommonService,
    tableService,
    cvTLModelPrimitiveService,
    albumPopulationBatchSize = conf.getInt("cv-model.album-population.batch-size"),
    albumPopulationParallelismLevel = conf.getInt("cv-model.album-population.parallelism-level")
  )
  lazy val fileUploadService = new FileUploadService(
    uploadedFilesStorage,
    filePathPrefix = conf.getString("file-uploading.target-prefix")
  )
  lazy val mlEntityExportImportService = new MLEntityExportImportService(
    cortexJobService,
    mlEntitiesStorage,
    conf.getBytes("entity-importing.max-meta-size")
  )
  lazy val projectService: ProjectService = new ProjectService(daoInstantiator.projectDao)
  lazy val cvModelService: CVModelService = new CVModelService(
    daoInstantiator.cvModelDao,
    daoInstantiator.cvTLModelPrimitiveDao,
    cortexJobService,
    cvModelCommonService,
    imagesCommonService,
    processService,
    assetSharingService,
    mlEntityExportImportService,
    projectService,
    cvTLModelPrimitiveService,
    dcProjectPackageService,
    mlEntitiesStorage
  ) with OnlineJobSharedAccess
    with PipelineSharedAccess
    with CVPredictionNestedUsage
    with ExperimentAggregatedAsset
    with OwnershipTransferRegistry.Member {
    protected val experimentDao: ExperimentDao = daoInstantiator.experimentDao
    protected val onlineJobDao: OnlineJobDao = daoInstantiator.onlineJobDao
    protected val cvPredictionDao: CVPredictionDao = daoInstantiator.cvPredictionDao
    protected val pipelineDao: PipelineDao = daoInstantiator.pipelineDao
    protected val pipelineOperatorDao: PipelineOperatorDao = daoInstantiator.pipelineOperatorDao
  }
  lazy val onlineJobService = new OnlineJobService(
    daoInstantiator.onlineJobDao,
    onlinePredictionConfigurator,
    assetSharingService,
    projectService
  ) with PipelineSharedAccess
    with ExperimentAggregatedAsset
    with OwnershipTransferRegistry.Member {
    protected val experimentDao: ExperimentDao = daoInstantiator.experimentDao
    protected val pipelineDao: PipelineDao = daoInstantiator.pipelineDao
    protected val pipelineOperatorDao: PipelineOperatorDao = daoInstantiator.pipelineOperatorDao
  }
  lazy val datasetService = new DatasetService(
    dao = daoInstantiator.datasetDao,
    projectService = projectService,
    fileStorage = datasetFilesStorage,
    fileStoragePrefix = conf.getString("dataset.storage-prefix"),
    assetSharingService = assetSharingService,
    s3BucketService = s3BucketService,
    processService = processService,
    cortexJobService = cortexJobService
  ) with PipelineSharedAccess
    with ExperimentAggregatedAsset
    with OwnershipTransferRegistry.Member {
    protected val experimentDao: ExperimentDao = daoInstantiator.experimentDao
    protected val pipelineDao: PipelineDao = daoInstantiator.pipelineDao
    protected val pipelineOperatorDao: PipelineOperatorDao = daoInstantiator.pipelineOperatorDao
  }
  lazy val cvPredictionService: CVPredictionService = new CVPredictionService(
    albumService,
    cvModelService,
    cvModelCommonService,
    tableService,
    cvTLModelPrimitiveService,
    imagesCommonService,
    pictureStorage,
    dcProjectPackageService,
    daoInstantiator.cvPredictionDao,
    cortexJobService,
    processService,
    assetSharingService,
    projectService
  ) with PipelineSharedAccess
    with ExperimentAggregatedAsset
    with OwnershipTransferRegistry.Member {
    protected val experimentDao: ExperimentDao = daoInstantiator.experimentDao
    protected val pipelineDao: PipelineDao = daoInstantiator.pipelineDao
    protected val pipelineOperatorDao: PipelineOperatorDao = daoInstantiator.pipelineOperatorDao
  }
  lazy val cvOnlinePredictionService: CVOnlinePredictionService = new CVOnlinePredictionService(
    daoInstantiator.albumDao,
    daoInstantiator.pictureDao
  )
  lazy val assetSharingService: AssetSharingService = new AssetSharingService(
    conf,
    mailService,
    umService,
    daoInstantiator.sharedResourceDao
  )
  lazy val tableService: TableService = new TableService(
    conf,
    tableDataDao,
    assetSharingService,
    LfuCache[String, Int],
    daoInstantiator.tableDao,
    processService,
    cortexJobService,
    projectService
  ) with PipelineSharedAccess
    with TabularPredictionNestedUsage
    with ExperimentAggregatedAsset
    with OwnershipTransferRegistry.Member {
    protected val experimentDao: ExperimentDao = daoInstantiator.experimentDao
    protected val tabularPredictionDao: TabularPredictionDao = daoInstantiator.tabularPredictionDao
    protected val pipelineDao: PipelineDao = daoInstantiator.pipelineDao
    protected val pipelineOperatorDao: PipelineOperatorDao = daoInstantiator.pipelineOperatorDao
  }
  lazy val tabularModelCommonService: TabularModelCommonService = new TabularModelCommonService(
    modelDao = daoInstantiator.tabularModelDao,
    experimentDao = daoInstantiator.experimentDao,
    tableService = tableService,
    probabilityColumnsPrefix = conf.getString("tabular-models.probability-columns-prefix")
  )
  lazy val tabularModelService: TabularModelService = new TabularModelService(
    dao = daoInstantiator.tabularModelDao,
    processService = processService,
    cortexJobService = cortexJobService,
    tableService = tableService,
    tabularModelCommonService = tabularModelCommonService,
    assetSharingService = assetSharingService,
    exportImportService = mlEntityExportImportService,
    projectService = projectService,
    packageService = dcProjectPackageService,
    mlEntitiesStorage = mlEntitiesStorage
  ) with PipelineSharedAccess
    with TabularPredictionNestedUsage
    with ExperimentAggregatedAsset
    with OwnershipTransferRegistry.Member {
    protected val tabularPredictionDao: TabularPredictionDao = daoInstantiator.tabularPredictionDao
    protected val experimentDao: ExperimentDao = daoInstantiator.experimentDao
    protected val pipelineDao: PipelineDao = daoInstantiator.pipelineDao
    protected val pipelineOperatorDao: PipelineOperatorDao = daoInstantiator.pipelineOperatorDao
  }
  lazy val tabularPredictionService = new TabularPredictionService(
    tabularModelService = tabularModelService,
    tableService = tableService,
    tabularModelCommonService = tabularModelCommonService,
    dao = daoInstantiator.tabularPredictionDao,
    assetSharingService = assetSharingService,
    cortexJobService = cortexJobService,
    processService = processService,
    projectService = projectService,
    packageService = dcProjectPackageService
  ) with PipelineSharedAccess
    with ExperimentAggregatedAsset
    with OwnershipTransferRegistry.Member {
    protected val experimentDao: ExperimentDao = daoInstantiator.experimentDao
    protected val pipelineDao: PipelineDao = daoInstantiator.pipelineDao
    protected val pipelineOperatorDao: PipelineOperatorDao = daoInstantiator.pipelineOperatorDao
  }
  lazy val dcProjectService = new DCProjectService(
    dao = daoInstantiator.dcProjectDao,
    projectService = projectService,
    sessionService = sessionService,
    fileStorage = dcProjectsFilesStorage,
    conf = conf.getConfig("dc-project"),
    processService = processService,
    assetSharingService = assetSharingService,
    packageDao = daoInstantiator.dcProjectPackageDao
  ) with PipelineSharedAccess
    with ExperimentAggregatedAsset
    with OwnershipTransferRegistry.Member {
    protected val experimentDao: ExperimentDao = daoInstantiator.experimentDao
    protected val pipelineDao: PipelineDao = daoInstantiator.pipelineDao
    protected val pipelineOperatorDao: PipelineOperatorDao = daoInstantiator.pipelineOperatorDao
  }
  lazy val sessionService = new SessionService(
    dcProjectDao = daoInstantiator.dcProjectDao,
    geminiService = geminiService,
    projectStorage = dcProjectStorage,
    sessionDao = daoInstantiator.sessionDao,
    projectStorageKeyPrefix = conf.getString("aws.key-prefix"),
    SessionNodeParams(
      conf.getDouble("jupyter.session.node-params.cpus"),
      conf.getDouble("jupyter.session.node-params.gpus")
    ),
    sessionMonitor
  )(ec, logger, materializer, Timeout(sessionMonitorStartupRequestTimeout * 2))
  lazy val dcProjectPackageService = new DCProjectPackageService(
    dao = daoInstantiator.dcProjectPackageDao,
    dcProjectService = dcProjectService,
    cvTLModelPrimitiveDao = daoInstantiator.cvTLModelPrimitiveDao,
    pipelineOperatorDao = daoInstantiator.pipelineOperatorDao,
    experimentDao = daoInstantiator.experimentDao,
    pipelineDao = daoInstantiator.pipelineDao,
    cortexJobService = cortexJobService,
    processService = processService,
    packageStorage = packageStorage,
    cvModelDao = daoInstantiator.cvModelDao,
    packageStorageKeyPrefix = conf.getString("package.storage-prefix"),
    categoryDao = daoInstantiator.categoryDao
  )

  lazy val cvModelTrainPipelineHandler = new CVModelTrainPipelineHandler(
    modelDao = daoInstantiator.cvModelDao,
    albumDao = daoInstantiator.albumDao,
    cvModelService = cvModelService,
    cvModelCommonService = cvModelCommonService,
    cvModelPrimitiveService = cvTLModelPrimitiveService,
    albumService = albumService,
    imagesCommonService = imagesCommonService,
    processService = processService,
    cortexJobService = cortexJobService,
    packageService = dcProjectPackageService,
    tableService = tableService
  )

  lazy val tabularTrainPipelineHandler = new TabularTrainPipelineHandler(
    modelDao = daoInstantiator.tabularModelDao,
    tableDao = daoInstantiator.tableDao,
    modelService = tabularModelService,
    tableService = tableService,
    tabularModelCommonService = tabularModelCommonService,
    cortexJobService = cortexJobService,
    processService = processService,
    packageService = dcProjectPackageService,
    conf = conf.getConfig("tabular-models")
  )

  lazy val genericExperimentPipelineHandler = new GenericExperimentPipelineHandler(
    cortexJobService = cortexJobService,
    processService = processService,
    pipelineOperatorDao = daoInstantiator.pipelineOperatorDao,
    packageService = dcProjectPackageService,
    pipelineService = pipelineService
  )

  lazy val experimentService = new ExperimentService(
    dao = daoInstantiator.experimentDao,
    projectService = projectService,
    processService = processService,
    assetSharingService = assetSharingService,
    experimentDelegator = new ExperimentDelegator(
      cvModelTrainPipelineHandler,
      tabularTrainPipelineHandler,
      genericExperimentPipelineHandler
    )
  )
    with TabularPredictionNestedUsage
    with CVPredictionNestedUsage
    with CVModelNestedUsage
    with OwnershipTransferRegistry.Member {
    override val tabularPredictionDao: TabularPredictionDao = daoInstantiator.tabularPredictionDao
    override val cvPredictionDao: CVPredictionDao = daoInstantiator.cvPredictionDao
    override val cvModelDao: CVModelDao = daoInstantiator.cvModelDao

    override def daoByAssetType(assetType: AssetType): EntityDao[_ <: Asset[_]] = assetType match {
      case AssetType.TabularModel => daoInstantiator.tabularModelDao
      case AssetType.TabularPrediction => tabularPredictionDao
      case AssetType.Table => daoInstantiator.tableDao
      case AssetType.Album => daoInstantiator.albumDao
      case AssetType.CvModel => cvModelDao
      case AssetType.CvPrediction => cvPredictionDao
      case AssetType.OnlineJob => daoInstantiator.onlineJobDao
      case AssetType.DCProject => daoInstantiator.dcProjectDao
      case AssetType.Experiment => daoInstantiator.experimentDao
      case AssetType.Pipeline => daoInstantiator.pipelineDao
      case AssetType.Dataset => daoInstantiator.datasetDao
      case AssetType.Flow => throw new RuntimeException(s"Invalid asset type $assetType")
    }
  }

  lazy val experimentCommonService = new ExperimentCommonService(
    experimentDao = daoInstantiator.experimentDao
  )

  lazy val pipelineService = new PipelineService(
    dao = daoInstantiator.pipelineDao,
    pipelineOperatorService = pipelineOperatorService,
    projectService = projectService,
    assetSharingService = assetSharingService
  ) with ExperimentAggregatedAsset
    with OwnershipTransferRegistry.Member {
    protected val experimentDao: ExperimentDao = daoInstantiator.experimentDao
  }

  lazy val pipelineOperatorService = new PipelineOperatorService(
    dao = daoInstantiator.pipelineOperatorDao,
    packageService = dcProjectPackageService
  )

  lazy val tabularConnectionInstantiator = new TabularConnectionInstantiator(conf.getConfig("tabular-storage"))

  lazy val tableDataDao: TableDataDao = new RedshiftTableDataDao(
    connectionProvider = tabularConnectionInstantiator.connectionProvider,
    fetchSize = conf.getInt("tabular-storage.fetch-size")
  )
  lazy val categoryService: CategoryService = new CategoryService(
    daoInstantiator.categoryDao
  )

  private lazy val http: HttpExt = Http()
  private lazy val storageS3Bucket = S3Bucket.AccessOptions(
    bucketName = conf.getString("aws.s3.bucketName"),
    region = conf.getString("aws.region"),
    accessKey = conf.getString("aws.access-key").toOption,
    secretKey = conf.getString("aws.secret-key").toOption,
    sessionToken = None
  )
  private val s3AccessPolicyFilePath = conf.getString("aws.policy.s3-access-policy-path")
  private val s3CredentialsDuration =  conf.getInt("aws.temporary-credentials-duration")
  private val s3CredentialsRoleArn = conf.getString("aws.temporary-credentials-role-arn")
  private val s3ArnPartition = conf.getString("aws.arn-partition")
  private lazy val sentranaServiceActor = {
    val defaultCachingSettings = CachingSettings(system)
    val lfuCacheSettings: LfuCacheSettings =
      defaultCachingSettings.lfuCacheSettings
        .withTimeToLive(toScalaFiniteDuration(conf.getDuration("um-service.user-token-ttl")))

    val userTokenCache = LfuCache[String, UserResponse](defaultCachingSettings.withLfuCacheSettings(lfuCacheSettings))

    system.actorOf(Props(new UMSentranaService(conf, http, userTokenCache)))
  }
  private lazy val cortexJobMetaStorage = new S3StorageService(
    storageS3Bucket,
    s3BucketService,
    s3AccessPolicyFilePath,
    s3CredentialsDuration,
    s3CredentialsRoleArn,
    s3ArnPartition
  )
  private lazy val datasetFilesStorage = new S3StorageService(
    storageS3Bucket,
    s3BucketService,
    s3AccessPolicyFilePath,
    s3CredentialsDuration,
    s3CredentialsRoleArn,
    s3ArnPartition
  )
  private lazy val pictureStorage = new PicturesS3StorageService(
    storageS3Bucket,
    s3BucketService,
    s3AccessPolicyFilePath,
    s3CredentialsDuration,
    s3CredentialsRoleArn,
    s3ArnPartition
  )
  private lazy val uploadedFilesStorage = new S3StorageService(
    storageS3Bucket,
    s3BucketService,
    s3AccessPolicyFilePath,
    s3CredentialsDuration,
    s3CredentialsRoleArn,
    s3ArnPartition
  )
  private lazy val mlEntitiesStorage = new S3StorageService(
    storageS3Bucket,
    s3BucketService,
    s3AccessPolicyFilePath,
    s3CredentialsDuration,
    s3CredentialsRoleArn,
    s3ArnPartition
  )
  private lazy val dcProjectsFilesStorage = new S3StorageService(
    storageS3Bucket,
    s3BucketService,
    s3AccessPolicyFilePath,
    s3CredentialsDuration,
    s3CredentialsRoleArn,
    s3ArnPartition
  )
  private lazy val packageStorage = new S3StorageService(
    storageS3Bucket,
    s3BucketService,
    s3AccessPolicyFilePath,
    s3CredentialsDuration,
    s3CredentialsRoleArn,
    s3ArnPartition
  )
  private lazy val dcProjectStorage = new S3StorageService(
    storageS3Bucket,
    s3BucketService,
    s3AccessPolicyFilePath,
    s3CredentialsDuration,
    s3CredentialsRoleArn,
    s3ArnPartition
  )
  private lazy val processMonitorStartupRequestTimeout = toScalaFiniteDuration(conf.getDuration(
    "process-monitor.startup-request-timeout"
  ))
  private lazy val processMonitorSingletonManagerProps = ClusterSingletonManager.props(
    singletonProps = ProcessMonitor.props(
      processDao = daoInstantiator.processDao,
      cortexJobService = cortexJobService,
      readStoredProcesses = conf.getBoolean("process-monitor.read-stored-processes"),
      processCheckInterval = toScalaFiniteDuration(conf.getDuration("process-monitor.process-check-interval")),
      startupRequestTimeout = processMonitorStartupRequestTimeout,
      handlersDependencySources = daoInstantiator, this
    ),
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system)
  )
  private lazy val processMonitorSingletonManager = system.actorOf(
    processMonitorSingletonManagerProps,
    "process-monitor-singleton-manager"
  )
  private lazy val processMonitor: ActorRef = system.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = processMonitorSingletonManager.path.toStringWithoutAddress,
      settings = ClusterSingletonProxySettings(system)
    ),
    "process-monitor-singleton-proxy"
  )

  private lazy val sessionMonitorStartupRequestTimeout = toScalaFiniteDuration(conf.getDuration(
    "jupyter.session.monitor.startup-request-timeout"
  ))
  private lazy val sessionMonitorSingletonManagerProps = ClusterSingletonManager.props(
    singletonProps = SessionMonitor.props(
      dcProjectDao = daoInstantiator.dcProjectDao,
      sessionDao = daoInstantiator.sessionDao,
      geminiService = geminiService,
      startupRequestTimeout = sessionMonitorStartupRequestTimeout
    ),
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system)
  )

  private lazy val sessionMonitorSingletonManager = system.actorOf(
    sessionMonitorSingletonManagerProps,
    "session-monitor-singleton-manager"
  )

  private lazy val sessionMonitor: ActorRef = system.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = sessionMonitorSingletonManager.path.toStringWithoutAddress,
      settings = ClusterSingletonProxySettings(system)
    ),
    "session-monitor-singleton-proxy"
  )

  private lazy val argoService = new ArgoService(conf, http)
  private lazy val geminiService = new GeminiService(conf, http)
  private lazy val onlinePredictionConfigurator = new OnlinePredictionConfigurator(
    conf,
    argoService,
    imagesCommonService,
    cvModelCommonService,
    albumService,
    cvModelService,
    s3BucketService
  )

}
