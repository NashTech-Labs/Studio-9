package cortex.jobmaster.jobs.job.cross_validation

import java.util.UUID

import cortex.TaskTimeInfo
import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.scheduler.TaskScheduler
import cortex.task.tabular_data.tabularpipeline.TabularPipelineModule
import cortex.task.tabular_data.tabularpipeline.TabularPipelineParams.{ TabularScoreParams, TabularTrainParams }
import cortex.task.tabular_data.{ AllowedModelPrimitive, AllowedTaskType }
import cortex.task.transform.splitter.SplitterParams.SplitterTaskResult
import cortex.task.{ HyperParam, StorageAccessParams }

import scala.concurrent.{ ExecutionContext, Future }

class CrossValidationJob(
    scheduler:                TaskScheduler,
    tabularModule:            TabularPipelineModule,
    storageAccessParams:      StorageAccessParams,
    crossValidationJobConfig: CrossValidationJobConfig
)(implicit val context: ExecutionContext) extends TaskIdGenerator {

  /**
   * Compute k-fold cross validation score
   */
  def getCVScore(
    jobId:          String,
    splitterResult: SplitterTaskResult,
    cvParams:       CrossValidationJob.CrossValidationParams,
    customPath:     Option[String]                           = None
  ): Future[(Double, JobTimeInfo.TasksTimeInfo)] = {
    require(splitterResult.parts.size > 1)
    val scores: Seq[Future[Option[(Double, Seq[TaskTimeInfo])]]] = splitterResult.parts.zipWithIndex.map {
      case (validateSplit, splitIndex) =>
        val trainPaths = splitterResult.parts.filter(_ != validateSplit)
        val salt = UUID.randomUUID().toString
        val cvTaskPath = customPath.getOrElse(s"$jobId/cross_validation/$salt/$splitIndex")
        for {
          trainResult <- {
            val trainParams = TabularTrainParams(
              trainInputPaths       = trainPaths,
              taskType              = cvParams.taskType,
              modelPrimitive        = cvParams.modelPrimitive,
              response              = cvParams.response,
              weightsCol            = cvParams.weightsCol,
              numericalPredictors   = cvParams.numericalPredictors,
              categoricalPredictors = cvParams.categoricalPredictors,
              hyperparamsDict       = cvParams.hyperparamsDict,
              storageAccessParams   = storageAccessParams,
              mvhModelId            = cvParams.mvhModelId,
              modelsBasePath        = cvParams.modelsBasePath
            )
            val trainTask = tabularModule.trainTask(
              id       = genTaskId(jobId),
              jobId    = jobId,
              taskPath = cvTaskPath,
              params   = trainParams,
              cpus     = crossValidationJobConfig.cpus,
              memory   = crossValidationJobConfig.taskMemoryLimit
            )
            trainTask.setAttempts(1)
            scheduler.submitTask(trainTask)
          }
          scoreResult <- {
            val scoreParams = TabularScoreParams(
              validateInputPaths  = Seq(validateSplit),
              modelId             = trainResult.modelReference.id,
              storageAccessParams = storageAccessParams,
              modelsBasePath      = cvParams.modelsBasePath
            )
            val scoreTask = tabularModule.scoreTask(
              id       = genTaskId(jobId),
              jobId    = jobId,
              taskPath = cvTaskPath,
              params   = scoreParams,
              cpus     = crossValidationJobConfig.cpus,
              memory   = crossValidationJobConfig.taskMemoryLimit
            )
            scoreTask.setAttempts(1)
            scheduler.submitTask(scoreTask)
          }
        } yield Some((scoreResult.scoreOutput, Seq(trainResult.taskTimeInfo, scoreResult.taskTimeInfo)))
    }

    val recoveredScores = scores.map(_.recover({
      //some of cv jobs can fail for some reason and in this case we just ignore them
      case _: Exception => None
    }))
    val meanScore = Future.sequence(recoveredScores).map(xs => {

      val (values, tasksTimeInfo) = xs.flatMap(_.toSeq).unzip

      val meanScore = if (values.nonEmpty) {
        values.sum / values.size
      } else {
        0
      }

      (meanScore, tasksTimeInfo.flatten)
    })
    meanScore
  }
}

object CrossValidationJob {
  case class CrossValidationParams(
      taskType:              AllowedTaskType,
      modelPrimitive:        AllowedModelPrimitive,
      numericalPredictors:   Seq[String],
      categoricalPredictors: Seq[String],
      response:              String,
      weightsCol:            Option[String],
      hyperparamsDict:       Map[String, HyperParam],
      mvhModelId:            String,
      modelsBasePath:        String
  )
}
