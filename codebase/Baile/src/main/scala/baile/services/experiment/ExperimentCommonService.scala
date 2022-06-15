package baile.services.experiment

import baile.dao.experiment.ExperimentDao
import baile.daocommons.WithId
import baile.domain.experiment.Experiment
import baile.domain.experiment.pipeline.ExperimentPipeline
import baile.domain.experiment.result.ExperimentResult

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag
import scala.util.Try

class ExperimentCommonService(experimentDao: ExperimentDao)(implicit ec: ExecutionContext) {

  private[services] def loadExperimentMandatory(id: String): Future[WithId[Experiment]] =
    experimentDao.get(id).map(_.getOrElse(throw new RuntimeException(
      s"Unexpectedly not found experiment $id in storage"
    )))

  private[services] def getExperimentPipelineAs[P <: ExperimentPipeline: ClassTag](
    experiment: Experiment
  ): Try[P] = Try {
    experiment.pipeline match {
      case pipeline: P => pipeline
      case pipeline => throw new RuntimeException(s"Unexpected experiment result type: '${ pipeline.getClass }'")
    }
  }

  private[services] def getExperimentResultAs[R <: ExperimentResult: ClassTag](
    experiment: Experiment
  ): Try[Option[R]] = Try {
    experiment.result.map {
      case result: R => result
      case result => throw new RuntimeException(s"Unexpected experiment result type: '${ result.getClass }'")
    }
  }

}
