package cortex.jobmaster.jobs.job.tabular

import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.jobmaster.jobs.job.splitter.SplitterJob
import cortex.jobmaster.jobs.job.tabular.TabularJob._
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.jobs.tuning.HyperParamSelector
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams
import cortex.task.common.ClassReference
import cortex.task.tabular_data._
import cortex.task.tabular_data.mvh.MVHModule
import cortex.task.tabular_data.mvh.MVHParams.MVHTrainParams
import cortex.task.tabular_data.tabularpipeline.TabularModelSummary.TabularModelSummary
import cortex.task.tabular_data.tabularpipeline.TabularPipelineModule
import cortex.task.tabular_data.tabularpipeline.TabularPipelineParams._
import cortex.task.transform.splitter.SplitterParams.SplitterTaskParams

import scala.concurrent.{ ExecutionContext, Future }

class TabularJob(
    scheduler:             TaskScheduler,
    hyperParamSelector:    HyperParamSelector,
    splitterJob:           SplitterJob,
    mvhModule:             MVHModule,
    tabularPipelineModule: TabularPipelineModule,
    storageAccessParams:   StorageAccessParams,
    tabularJobConfig:      TabularJobConfig,
    modelsBasePath:        String,
    tmpDirPath:            String
)(implicit val context: ExecutionContext) extends TaskIdGenerator {

  def trainPredict(
    jobId:              String,
    tabularJobTPParams: TabularJobTrainPredictParams
  ): Future[(TabularTrainPredictResults, JobTimeInfo.TasksTimeInfo)] = {
    for {
      //restoring missing values
      mvhResult <- {
        val mvhParams = MVHTrainParams(
          trainInputPaths       = tabularJobTPParams.trainInputPaths,
          numericalPredictors   = tabularJobTPParams.numericalPredictors,
          categoricalPredictors = tabularJobTPParams.categoricalPredictors,
          storageAccessParams   = storageAccessParams,
          modelsBasePath        = modelsBasePath
        )
        val taskId = genTaskId(jobId)
        val mvhTask = mvhModule.trainTask(
          id       = taskId,
          jobId    = jobId,
          taskPath = s"$jobId/mvh_train",
          params   = mvhParams,
          cpus     = tabularJobConfig.cpus,
          memory   = tabularJobConfig.taskMemoryLimit
        )
        scheduler.submitTask(mvhTask)

      }
      //splitting output of mvh for selection stage
      splitterResult <- {
        val splitterTaskParams = SplitterTaskParams(tabularJobTPParams.trainInputPaths, storageAccessParams)
        splitterJob.splitInput(
          jobId              = jobId,
          splitterTaskParams = splitterTaskParams
        )
      }
      //selecting best model and it's hyper parameters
      (selectionResult, jobTasksTimeInfo) <- {
        val hpParams = HyperParamSelector.HPSelectorParams(
          taskType              = tabularJobTPParams.taskType,
          weightsCol            = tabularJobTPParams.weightsCol,
          response              = tabularJobTPParams.response,
          numericalPredictors   = tabularJobTPParams.numericalPredictors,
          categoricalPredictors = tabularJobTPParams.categoricalPredictors,
          mvhModelId            = mvhResult.modelReference.id,
          modelsBasePath        = modelsBasePath
        )
        hyperParamSelector.getBestHyperParams(
          jobId            = jobId,
          numHPSamples     = tabularJobConfig.numHPSamples,
          splitterResult   = splitterResult,
          hpSelectorParams = hpParams
        )
      }
      //training, predicting using best model with configured hyper params
      tabularResult <- {
        val selectedModel = selectionResult.bestScore.modelWithHyperParams.modelPrimitive
        val selectedHyperParams = selectionResult.bestScore.modelWithHyperParams.hyperParams
        val tabularTrainPredictParams = TabularTrainPredictParams(
          trainInputPaths       = tabularJobTPParams.trainInputPaths,
          mvhModelId            = mvhResult.modelReference.id,
          taskType              = tabularJobTPParams.taskType,
          modelPrimitive        = selectedModel,
          response              = tabularJobTPParams.response,
          numericalPredictors   = tabularJobTPParams.numericalPredictors,
          categoricalPredictors = tabularJobTPParams.categoricalPredictors,
          predictColName        = tabularJobTPParams.predictColName,
          probabilityColPrefix  = tabularJobTPParams.probabilityColPrefix,
          weightsCol            = tabularJobTPParams.weightsCol,
          hyperparamsDict       = selectedHyperParams,
          storageAccessParams   = storageAccessParams,
          modelsBasePath        = modelsBasePath,
          predictPath           = s"$tmpDirPath/$jobId/tabular_train_predict/predict"
        )
        val taskId = genTaskId(jobId)
        val task = tabularPipelineModule
          .trainPredictTask(
            id       = taskId,
            jobId    = jobId,
            taskPath = s"$jobId/tabular_train_predict",
            params   = tabularTrainPredictParams,
            cpus     = tabularJobConfig.cpus,
            memory   = tabularJobConfig.taskMemoryLimit
          )
        scheduler.submitTask(task)
      }
    } yield (
      TabularTrainPredictResults(
        modelReference      = tabularResult.modelReference,
        predictPath         = tabularResult.predictPath,
        probabilityColNames = tabularResult.probabilityColNames.toMap,
        headerColNames      = tabularResult.headerColNames,
        tabularModelSummary = tabularResult.summaryStats,
        bestModel           = selectionResult.bestScore.modelWithHyperParams.modelPrimitive
      ), jobTasksTimeInfo ++ Seq(mvhResult.taskTimeInfo, splitterResult.taskTimeInfo, tabularResult.taskTimeInfo)
    )
  }

  def evaluate(
    jobId:                    String,
    tabularJobEvaluateParams: TabularJobEvaluateParams
  ): Future[(TabularEvaluateResult, JobTimeInfo.TasksTimeInfo)] = {
    for {
      tabularResult <- {
        val tabularEvaluateParams = TabularEvaluateParams(
          validateInputPaths  = tabularJobEvaluateParams.inputDataPaths,
          weightsCol          = tabularJobEvaluateParams.weightsCol,
          modelId             = tabularJobEvaluateParams.modelId,
          storageAccessParams = storageAccessParams,
          columnsMapping      = tabularJobEvaluateParams.columnsMapping,
          predictColName      = tabularJobEvaluateParams.predictColName,
          probabilityColNames = tabularJobEvaluateParams.probabilityColNames.toList,
          classReference      = tabularJobEvaluateParams.classReference,
          modelsBasePath      = modelsBasePath,
          predictPath         = s"$tmpDirPath/$jobId/tabular_evaluate/predict"
        )
        val taskId = genTaskId(jobId)
        val evaluateTask = tabularPipelineModule.evaluateTask(
          id       = taskId,
          jobId    = jobId,
          taskPath = s"$jobId/tabular_evaluate",
          params   = tabularEvaluateParams,
          cpus     = tabularJobConfig.cpus,
          memory   = tabularJobConfig.taskMemoryLimit
        )
        scheduler.submitTask(evaluateTask)
      }
    } yield (tabularResult, Seq(tabularResult.taskTimeInfo))
  }

  def predict(
    jobId:                   String,
    tabularJobPredictParams: TabularJobPredictParams
  ): Future[(TabularPredictResult, JobTimeInfo.TasksTimeInfo)] = {
    for {
      tabularResult <- {
        val tabularParams = TabularPredictParams(
          validateInputPaths  = tabularJobPredictParams.inputDataPaths,
          modelId             = tabularJobPredictParams.modelId,
          storageAccessParams = storageAccessParams,
          columnsMapping      = tabularJobPredictParams.columnsMapping,
          predictColName      = tabularJobPredictParams.predictColName,
          probabilityColNames = tabularJobPredictParams.probabilityColNames.toList,
          classReference      = tabularJobPredictParams.classReference,
          modelsBasePath      = modelsBasePath,
          predictPath         = s"$tmpDirPath/$jobId/tabular_predict/predict"
        )
        val taskId = genTaskId(jobId)
        val pTask = tabularPipelineModule.predictTask(
          id       = taskId,
          jobId    = jobId,
          taskPath = s"$jobId/tabular_predict",
          params   = tabularParams,
          cpus     = tabularJobConfig.cpus,
          memory   = tabularJobConfig.taskMemoryLimit
        )
        scheduler.submitTask(pTask)
      }
    } yield (tabularResult, Seq(tabularResult.taskTimeInfo))
  }
}

object TabularJob {

  case class TabularJobTrainPredictParams(
      trainInputPaths:       Seq[String],
      taskType:              AllowedTaskType,
      numericalPredictors:   Seq[String],
      categoricalPredictors: Seq[String],
      response:              String,
      weightsCol:            Option[String],
      predictColName:        String,
      probabilityColPrefix:  Option[String]
  )

  case class TabularJobPredictParams(
      inputDataPaths:      Seq[String],
      modelId:             String,
      columnsMapping:      Map[String, String],
      predictColName:      String,
      probabilityColNames: Map[String, String],
      classReference:      ClassReference
  )

  case class TabularJobEvaluateParams(
      inputDataPaths:      Seq[String],
      columnsMapping:      Map[String, String],
      predictColName:      String,
      probabilityColNames: Map[String, String],
      weightsCol:          Option[String],
      modelId:             String,
      classReference:      ClassReference
  )

  case class TabularTrainPredictResults(
      modelReference:      ModelReference,
      headerColNames:      Seq[String],
      probabilityColNames: Map[String, String],
      predictPath:         String,
      tabularModelSummary: TabularModelSummary,
      bestModel:           AllowedModelPrimitive
  )

}
