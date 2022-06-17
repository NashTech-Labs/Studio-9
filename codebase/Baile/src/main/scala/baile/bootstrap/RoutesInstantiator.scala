package baile.bootstrap

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import baile.routes.BaseRoutes
import baile.routes.asset.sharing.AssetSharingRoutes
import baile.routes.common.CommonRoutes
import baile.routes.cv.model.CVModelRoutes
import baile.routes.cv.prediction.CVPredictionRoutes
import baile.routes.dcproject.{ DCProjectPackageRoutes, DCProjectRoutes, SessionRoutes }
import baile.routes.experiment.ExperimentRoutes
import baile.routes.dataset.DatasetRoutes
import baile.routes.images.ImagesRoutes
import baile.routes.info.InfoRoutes
import baile.routes.onlineJob.OnlineJobRoutes
import baile.routes.pipeline.{ PipelineOperatorRoutes, PipelineRoutes }
import baile.routes.process.ProcessRoutes
import baile.routes.project.ProjectRoutes
import baile.routes.table.TableRoutes
import baile.routes.tabular.model.TabularModelRoutes
import baile.routes.tabular.prediction.TabularPredictionRoutes
import baile.routes.usermanagement.UMRoutes
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.ExecutionContext

class RoutesInstantiator(
  conf: Config,
  services: ServiceInstantiator,
  appUrl: String
)(
  implicit val ec: ExecutionContext
) extends PlayJsonSupport {

  private val infoRoutes = new InfoRoutes()
  private val commonRoutes = new CommonRoutes(
    conf,
    services.authenticationService,
    services.s3BucketService,
    services.cvModelService,
    services.tableService,
    services.tabularModelService,
    services.albumService,
    services.projectService,
    services.defaultAugmentationValuesService,
    services.cvTLModelPrimitiveService,
    services.categoryService,
    services.datasetService,
    services.experimentService,
    services.pipelineService,
    services.dcProjectService,
    services.cvPredictionService,
    services.tabularPredictionService
  )
  private val cvModelRoutes = new CVModelRoutes(
    conf,
    services.authenticationService,
    services.cvModelService,
    services.fileUploadService,
    appUrl
  )
  private val umRoutes = new UMRoutes(services.umService, services.authenticationService, conf)
  private val imagesRoutes = new ImagesRoutes(
    conf,
    services.authenticationService,
    services.albumService,
    services.pictureService,
    services.imageUploadService,
    services.fileUploadService
  )
  private val processRoutes = new ProcessRoutes(conf, services.processService, services.authenticationService)
  private val sharedResourceRoutes = new AssetSharingRoutes(
    conf,
    services.authenticationService,
    services.assetSharingService
  )
  private val onlineJobRoutes = new OnlineJobRoutes(conf, services.authenticationService, services.onlineJobService)
  private val cvPredictionRoutes = new CVPredictionRoutes(
    conf,
    services.authenticationService,
    services.cvPredictionService
  )
  private val datasetRoutes = new DatasetRoutes(
    conf,
    services.authenticationService,
    services.fileUploadService,
    services.datasetService
  )
  private val projectRoutes = new ProjectRoutes(
    conf,
    services.authenticationService,
    services.projectService,
    services.albumService,
    services.cvModelService,
    services.cvPredictionService,
    services.onlineJobService,
    services.tableService,
    services.dcProjectService,
    services.experimentService,
    services.pipelineService,
    services.datasetService,
    services.tabularModelService,
    services.tabularPredictionService
  )
  private val tableRoutes = new TableRoutes(
    conf,
    services.authenticationService,
    services.tableService,
    services.fileUploadService
  )
  private val tabularModelRoutes = new TabularModelRoutes(
    conf = conf,
    service = services.tabularModelService,
    authenticationService = services.authenticationService,
    services.fileUploadService,
    appUrl = appUrl
  )
  private val tabularPredictionRoutes = new TabularPredictionRoutes(
    conf = conf,
    authenticationService = services.authenticationService,
    service = services.tabularPredictionService,
    tabularModelService = services.tabularModelService
  )
  private val dcProjectRoutes = new DCProjectRoutes(
    conf = conf,
    authenticationService = services.authenticationService,
    service = services.dcProjectService
  )
  private val sessionRoutes = new SessionRoutes(
    conf = conf,
    authenticationService = services.authenticationService,
    service = services.sessionService
  )
  private val dcProjectPackageRoutes = new DCProjectPackageRoutes(
    conf = conf,
    authenticationService = services.authenticationService,
    service = services.dcProjectPackageService
  )
  private val experimentRoutes = new ExperimentRoutes(
    conf = conf,
    authenticationService = services.authenticationService,
    service = services.experimentService
  )
  private val pipelineRoutes = new PipelineRoutes(
    conf = conf,
    authenticationService = services.authenticationService,
    service = services.pipelineService
  )
  private val pipelineOperatorRoutes = new PipelineOperatorRoutes(
    conf = conf,
    authenticationService = services.authenticationService,
    service = services.pipelineOperatorService
  )

  private val corsSettings = CorsSettings.defaultSettings
    .withAllowedMethods(scala.collection.immutable.Seq(GET, POST, PUT, HEAD, DELETE, OPTIONS))

  val routes: Route = cors(corsSettings) {
    BaseRoutes.seal(conf) {
      ignoreTrailingSlash {
        concat(
          infoRoutes.routes,
          commonRoutes.routes,
          processRoutes.routes,
          umRoutes.routes,
          cvModelRoutes.routes,
          imagesRoutes.routes,
          onlineJobRoutes.routes,
          sharedResourceRoutes.routes,
          cvPredictionRoutes.routes,
          projectRoutes.routes,
          tableRoutes.routes,
          tabularModelRoutes.routes,
          tabularPredictionRoutes.routes,
          dcProjectRoutes.routes,
          sessionRoutes.routes,
          dcProjectPackageRoutes.routes,
          experimentRoutes.routes,
          pipelineOperatorRoutes.routes,
          pipelineRoutes.routes,
          datasetRoutes.routes
        )
      }
    }
  }
}
