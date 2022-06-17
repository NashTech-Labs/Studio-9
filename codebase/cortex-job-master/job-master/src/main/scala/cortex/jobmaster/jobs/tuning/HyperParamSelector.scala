package cortex.jobmaster.jobs.tuning

import cortex.jobmaster.jobs.job.cross_validation.CrossValidationJob
import cortex.jobmaster.jobs.job.cross_validation.CrossValidationJob.CrossValidationParams
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.jobs.tuning.HyperParamRandomSearch.ModelWithHyperParams
import cortex.task.tabular_data.AllowedTaskType
import cortex.task.transform.splitter.SplitterParams.SplitterTaskResult

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Job for best hyper params selection
 */
class HyperParamSelector(
    crossValidationJob:     CrossValidationJob,
    hyperParamRandomSearch: HyperParamRandomSearch
)(implicit val context: ExecutionContext) {
  import HyperParamSelector._

  def getBestHyperParams(
    jobId:            String,
    numHPSamples:     Int,
    splitterResult:   SplitterTaskResult,
    hpSelectorParams: HPSelectorParams
  ): Future[(SelectionResult, JobTimeInfo.TasksTimeInfo)] = {

    val models: Seq[ModelWithHyperParams] =
      hyperParamRandomSearch.getHyperParams(hpSelectorParams.taskType, numHPSamples)

    val scores: Seq[Future[(CVScore, JobTimeInfo.TasksTimeInfo)]] = models.map(modelWithHyperParams => {
      val cvParams = CrossValidationParams(
        taskType              = hpSelectorParams.taskType,
        modelPrimitive        = modelWithHyperParams.modelPrimitive,
        response              = hpSelectorParams.response,
        weightsCol            = hpSelectorParams.weightsCol,
        numericalPredictors   = hpSelectorParams.numericalPredictors,
        categoricalPredictors = hpSelectorParams.categoricalPredictors,
        hyperparamsDict       = modelWithHyperParams.hyperParams,
        mvhModelId            = hpSelectorParams.mvhModelId,
        modelsBasePath        = hpSelectorParams.modelsBasePath
      )
      crossValidationJob.getCVScore(
        jobId          = jobId,
        splitterResult = splitterResult,
        cvParams       = cvParams
      ).map {
        case (result, tasksTimeInfo) => (CVScore(modelWithHyperParams, result), tasksTimeInfo)
      }
    })

    for {
      historyWithTimeInfo <- Future.sequence(scores)
      (history, jobsTasksTimeInfo) = historyWithTimeInfo.unzip
      highestScore = history.reduce[CVScore]((bs, s) => if (s.score > bs.score) s else bs)
    } yield (SelectionResult(history, highestScore), jobsTasksTimeInfo.flatten)
  }
}

object HyperParamSelector {

  case class HPSelectorParams(
      taskType:              AllowedTaskType,
      weightsCol:            Option[String],
      response:              String,
      numericalPredictors:   Seq[String],
      categoricalPredictors: Seq[String],
      mvhModelId:            String,
      modelsBasePath:        String
  )

  case class SelectionResult(history: Seq[CVScore], bestScore: CVScore)

  case class CVScore(modelWithHyperParams: ModelWithHyperParams, score: Double)
}
