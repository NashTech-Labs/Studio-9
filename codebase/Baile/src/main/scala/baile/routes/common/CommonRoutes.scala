package baile.routes.common

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import baile.daocommons.filters.TrueFilter
import baile.domain.asset.AssetScope
import baile.domain.cv.model.tlprimitives.CVTLModelPrimitiveType
import baile.domain.usermanagement.User
import baile.routes.AuthenticatedRoutes
import baile.routes.contract.asset.AssetScopeReads
import baile.routes.contract.common.{ ListResponse, S3BucketInfoResponse, UserStatsResponse }
import baile.routes.contract.cv.{ CVArchitectureResponse, CVModelTypeResponse }
import baile.routes.contract.images.augmentation.AlbumAugmentationStep
import baile.routes.contract.pipeline.category.CategoryResponse
import baile.services.common.{ AuthenticationService, S3BucketService }
import baile.services.cv.CVTLModelPrimitiveService
import baile.services.cv.model.CVModelService
import baile.services.cv.prediction.CVPredictionService
import baile.services.dataset.DatasetService
import baile.services.dcproject.DCProjectService
import baile.services.experiment.ExperimentService
import baile.services.images.{ AlbumService, DefaultAugmentationValuesService }
import baile.services.pipeline.PipelineService
import baile.services.pipeline.category.CategoryService
import baile.services.project.ProjectService
import baile.services.table.TableService
import baile.services.tabular.model.TabularModelService
import baile.services.tabular.prediction.TabularPredictionService
import com.amazonaws.regions.Regions
import com.typesafe.config.Config
import play.api.libs.json.{ JsArray, JsString }

import scala.concurrent.ExecutionContext

class CommonRoutes(
  conf: Config,
  val authenticationService: AuthenticationService,
  s3BucketService: S3BucketService,
  cvModelService: CVModelService,
  tableService: TableService,
  tabularModelService: TabularModelService,
  albumService: AlbumService,
  projectService: ProjectService,
  augmentationValuesService: DefaultAugmentationValuesService,
  cvModelPrimitiveService: CVTLModelPrimitiveService,
  categoryService: CategoryService,
  datasetService: DatasetService,
  experimentService: ExperimentService,
  pipelineService: PipelineService,
  dcProjectService: DCProjectService,
  cvPredictionService: CVPredictionService,
  tabularPredictionService: TabularPredictionService
)(
  implicit val ec: ExecutionContext
) extends AuthenticatedRoutes {

  val routes: Route = authenticated { authParams =>
    implicit val user: User = authParams.user

    concat(
      pathPrefix("config") {
        concat(
          path("operator-categories") {
            get {
              onSuccess(categoryService.listAll) { categories =>
                complete(categories.map(CategoryResponse.fromDomain))
              }
            }
          },
          path("aws-regions") {
            get {
              complete(JsArray(Regions.values.map { region =>
                JsString(region.getName)
              }))
            }
          },
          path("cv-data-augmentation-defaults") {
            get {
              complete(augmentationValuesService.getDefaultAugmentationValues.map(AlbumAugmentationStep.fromDomain))
            }
          },
          get {
            concat(
              path("cv-architectures") {
                onSuccess(cvModelPrimitiveService.getCVTLModelPrimitivesWithPackageInfo(CVTLModelPrimitiveType.UTLP)) {
                  architectureOperatorExtendedResponseList =>complete(architectureOperatorExtendedResponseList.map(
                    CVArchitectureResponse.fromDomain
                  ))
                }
              },
              path("cv-classifiers") {
                onSuccess(cvModelPrimitiveService.getCVTLModelPrimitivesWithPackageInfo(
                  CVTLModelPrimitiveType.Classifier
                )) {
                  classifierOperatorExtendedResponseList => complete(classifierOperatorExtendedResponseList.map(
                    CVModelTypeResponse.fromDomain
                  ))
                }
              },
              path("cv-detectors") {
                onSuccess(cvModelPrimitiveService.getCVTLModelPrimitivesWithPackageInfo(
                  CVTLModelPrimitiveType.Detector
                )) {
                  detectorOperatorExtendedResponseList => complete(detectorOperatorExtendedResponseList.map(
                    CVModelTypeResponse.fromDomain
                  ))
                }
              },
              path("cv-decoders") {
                onSuccess(cvModelPrimitiveService.getCVTLModelPrimitivesWithPackageInfo(
                  CVTLModelPrimitiveType.Decoder
                )) {
                  encoderOperatorExtendedResponseList => complete(encoderOperatorExtendedResponseList.map(
                    CVModelTypeResponse.fromDomain
                  ))
                }
              }
            )
          }
        )
      },
      (path("s3buckets") & get) {
        onSuccess(s3BucketService.listAll()) { buckets =>
          complete(ListResponse(buckets.map(S3BucketInfoResponse.fromDomain), buckets.length))
        }
      } ~
      (path("me" / "stats") & (get & parameters(
        'scope.as[AssetScope](fromStringUnmarshaller[AssetScope]).?
      ))) { scope =>

        def getCount(f: Either[_, Int]): Int = f.getOrElse(0)

        val result = for {
          tablesCount <- tableService.count(scope, None, None, None)
          tabularModelsCount <- tabularModelService.count(scope, None, None, None)
          cvModelsCount <- cvModelService.count(scope, None, None, None)
          albumsCount <- albumService.count(scope, None, None, None)
          projectsCount <- projectService.count(TrueFilter)
          binaryDatasetsCount <- datasetService.count(scope, None, None, None)
          pipelinesCount <- pipelineService.count(scope, None, None, None)
          experimentsCount <- experimentService.count(scope, None, None, None)
          dcProjectsCount <- dcProjectService.count(scope, None, None, None)
          cvPredictionsCount <- cvPredictionService.count(scope, None, None, None)
          tabularPredictionsCount <- tabularPredictionService.count(scope, None, None, None)
        } yield UserStatsResponse(
          tablesCount = getCount(tablesCount),
          flowsCount = 0,
          modelsCount = getCount(tabularModelsCount),
          projectsCount = getCount(projectsCount),
          cvModelsCount = getCount(cvModelsCount),
          albumsCount = getCount(albumsCount),
          binaryDatasetsCount = getCount(binaryDatasetsCount),
          experimentsCount = getCount(experimentsCount),
          pipelinesCount = getCount(pipelinesCount),
          dcProjectsCount = getCount(dcProjectsCount),
          cvPredictionsCount = getCount(cvPredictionsCount),
          tabularPredictionsCount = getCount(tabularPredictionsCount)
        )

        onSuccess(result) { stats =>
          complete(stats)
        }
      }
    )
  }
}
