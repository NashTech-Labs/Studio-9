package baile.bootstrap

import baile.dao.asset.sharing.SharedResourceDao
import baile.dao.cv.model._
import baile.dao.cv.model.tlprimitives.CVTLModelPrimitiveDao
import baile.dao.cv.prediction.CVPredictionDao
import baile.dao.dcproject.{ DCProjectDao, DCProjectPackageDao, SessionDao }
import baile.dao.experiment.{ ExperimentDao, SerializerDelegator }
import baile.dao.dataset.DatasetDao
import baile.dao.images.{ AlbumDao, PictureDao }
import baile.dao.onlinejob.OnlineJobDao
import baile.dao.pipeline.category.CategoryDao
import baile.dao.pipeline.{ GenericExperimentPipelineSerializer, PipelineDao, PipelineOperatorDao }
import baile.dao.process.ProcessDao
import baile.dao.project.ProjectDao
import baile.dao.table.TableDao
import baile.dao.tabular.model.{ TabularModelDao, TabularModelPipelineSerializer }
import baile.dao.tabular.prediction.TabularPredictionDao
import org.mongodb.scala.MongoDatabase

class DaoInstantiator(database: MongoDatabase) {

  lazy val tabularModelPipelineSerializer = new TabularModelPipelineSerializer
  lazy val cvModelPipelineSerializer = new CVModelPipelineSerializer
  lazy val genericExperimentPipelineSerializer = new GenericExperimentPipelineSerializer
  lazy val serializerDelegator = new SerializerDelegator(
    tabularModelPipelineSerializer,
    cvModelPipelineSerializer,
    genericExperimentPipelineSerializer
  )

  lazy val cvModelDao: CVModelDao = new CVModelDao(database)

  lazy val albumDao: AlbumDao = new AlbumDao(database)

  lazy val pictureDao: PictureDao = new PictureDao(database)

  lazy val processDao: ProcessDao = new ProcessDao(database)

  lazy val sharedResourceDao: SharedResourceDao = new SharedResourceDao(database)

  lazy val onlineJobDao: OnlineJobDao = new OnlineJobDao(database)

  lazy val cvPredictionDao: CVPredictionDao = new CVPredictionDao(database)

  lazy val projectDao: ProjectDao = new ProjectDao(database)

  lazy val tableDao: TableDao = new TableDao(database)

  lazy val tabularPredictionDao: TabularPredictionDao = new TabularPredictionDao(database)

  lazy val tabularModelDao: TabularModelDao = new TabularModelDao(database)

  lazy val dcProjectDao: DCProjectDao = new DCProjectDao(database)

  lazy val sessionDao: SessionDao = new SessionDao(database)

  lazy val dcProjectPackageDao: DCProjectPackageDao = new DCProjectPackageDao(database)

  lazy val cvTLModelPrimitiveDao: CVTLModelPrimitiveDao = new CVTLModelPrimitiveDao(database)

  lazy val pipelineOperatorDao: PipelineOperatorDao = new PipelineOperatorDao(database)

  lazy val experimentDao: ExperimentDao = new ExperimentDao(database, serializerDelegator)

  lazy val pipelineDao: PipelineDao = new PipelineDao(database)

  lazy val datasetDao: DatasetDao = new DatasetDao(database)

  lazy val categoryDao: CategoryDao = new CategoryDao(database)

}
