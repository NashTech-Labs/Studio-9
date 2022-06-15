package cortex.jobmaster.orion.service.domain

import cortex.CortexException
import cortex.task.TabularAccessParams.RedshiftAccessParams
import cortex.api.job.table.{ DataSource, ProbabilityClassColumn }
import cortex.api.job.JobType.{ TabularEvaluate, TabularModelImport, TabularPredict, TabularTrain }
import cortex.api.job.tabular._
import cortex.api.job.JobRequest
import cortex.api.job.common.ModelReference
import cortex.common.Logging
import cortex.common.future.FutureExtensions._
import cortex.common.logging.JMLoggerFactory
import cortex.io.S3Client
import cortex.jobmaster.jobs.job.CommonConverters.fromCortexClassReference
import cortex.jobmaster.jobs.job.cross_validation.CrossValidationJob
import cortex.jobmaster.jobs.job.redshift_exporter.RedshiftExporterJob
import cortex.jobmaster.jobs.job.redshift_importer.RedshiftImporterJob
import cortex.jobmaster.jobs.job.splitter.SplitterJob
import cortex.jobmaster.jobs.job.tabular.TabularJob._
import cortex.jobmaster.jobs.job.tabular.{ TableExporterJob, TableImporterJob, TabularJob, TabularModelImportJob }
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.jobs.tuning.{ HyperParamRandomSearch, HyperParamSelector }
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler.{ JobId, JobResult }
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.column.ColumnDataType
import cortex.task.tabular_data.TabularModelImportParams.TabularModelImportTaskParams
import cortex.task.tabular_data.mvh.MVHModule
import cortex.task.tabular_data.tabularpipeline.TabularModelSummary._
import cortex.task.tabular_data.tabularpipeline.{ TabularModelSummary, TabularPipelineModule }
import cortex.task.tabular_data.{ AllowedTaskType, TabularModelImportModule }
import cortex.task.transform.common.{ Column, TableFileType }
import cortex.task.transform.exporter.redshift.RedshiftExporterModule
import cortex.task.transform.importer.redshift.RedshiftImporterModule
import cortex.task.transform.splitter.SplitterModule
import cortex.task.StorageAccessParams

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class TabularDataService(
    tabularJob:          TabularJob,
    importerJob:         TableImporterJob,
    exporterJob:         TableExporterJob,
    modelImportJob:      TabularModelImportJob,
    storageAccessParams: StorageAccessParams.S3AccessParams,
    modelsBasePath:      String,
    tmpDirPath:          String
)(implicit executionContext: ExecutionContext, val loggerFactory: JMLoggerFactory) extends JobRequestPartialHandler with Logging {

  def trainPredict(jobId: JobId, trainRequest: TrainRequest): Future[(TrainResult, JobTimeInfo)] = {
    log.info("[train predict] starting train predict process")

    def getTrainPredictParams(trainRequest: TrainRequest) = Try {
      val response = trainRequest.response
        .getOrElse(throw new CortexException("response is empty"))
      val taskType = {
        if (response.variableType.isContinuous) {
          AllowedTaskType.Regressor
        } else {
          AllowedTaskType.Classifier
        }
      }
      val numericPredictors = trainRequest.predictors.filter(_.variableType.isContinuous)
      val categoricalPredictors = trainRequest.predictors.filter(_.variableType.isCategorical)
      (taskType, numericPredictors, categoricalPredictors, response)
    }

    val result = for {
      (inputDataSource, outputDataSource) <- Try {
        val input = trainRequest.input.getOrElse(throw new CortexException("input datasource is empty"))
        val output = trainRequest.output.getOrElse(throw new CortexException("output datasource is empty"))
        (input, output)
      }.toFuture

      //prepare params
      (taskType, numericPredictors, categoricalPredictors, response) <- getTrainPredictParams(trainRequest).toFuture

      //importing table from external database as csv to s3
      inputTablePath = s"$tmpDirPath/tables/$jobId/input.csv"
      importTaskResult <- {
        log.info("[train predict] starting import table from database")
        importFromDatabase(jobId, inputDataSource, inputTablePath)
      }

      //starting train predict job
      (tpRes, jobTasksTimeInfo) <- {
        val params = TabularJobTrainPredictParams(
          trainInputPaths       = Seq(importTaskResult.outputS3Path),
          taskType              = taskType,
          numericalPredictors   = numericPredictors.map(_.name),
          categoricalPredictors = categoricalPredictors.map(_.name),
          weightsCol            = trainRequest.weight.map(_.name),
          predictColName        = trainRequest.predictionResultColumnName,
          probabilityColPrefix  = trainRequest.probabilityColumnsPrefix,
          response              = response.name
        )
        log.info("[train predict] starting train predict")
        tabularJob.trainPredict(jobId, params)
      }

      //saving a result to external system
      outputTableColumns <- getOutputColumns(
        inputDataSource      = inputDataSource,
        responseType         = TableConverters.apiDataTypeToDomain(response.dataType),
        predictionColumnName = trainRequest.predictionResultColumnName,
        probabilityColNames  = tpRes.probabilityColNames.values.toSeq,
        headerColNames       = tpRes.headerColNames
      ).toFuture
      exportJobTasksTimeInfo <- {
        log.info("[train predict] starting export to database")
        exportToDatabase(
          jobId      = jobId,
          dataSource = outputDataSource,
          columns    = outputTableColumns,
          s3SrcPath  = tpRes.predictPath,
          dropTable  = trainRequest.dropPreviousResultTable
        )
      }
    } yield {
      val modelType = getModelType(tpRes.tabularModelSummary)
      val formula = tpRes.tabularModelSummary
        .formula.getOrElse(throw new CortexException("formula can't be empty while trainPredict step"))
      val predictorsSummary = getPredictorsSummary(tpRes.tabularModelSummary)
      (TrainResult(
        modelId            = tpRes.modelReference.id,
        modelFilePath      = tpRes.modelReference.path,
        modelType          = modelType,
        formula            = formula,
        output             = Some(outputDataSource),
        summary            = translateSummary(tpRes.tabularModelSummary),
        modelPrimitive     = tpRes.bestModel.toString,
        predictorsSummary  = predictorsSummary,
        probabilityColumns = tpRes.probabilityColNames.map {
          case (className, columnName) => ProbabilityClassColumn(
            className,
            columnName
          )
        }.toSeq
      ), JobTimeInfo(Seq(importTaskResult.taskTimeInfo) ++ jobTasksTimeInfo ++ exportJobTasksTimeInfo))
    }

    result
  }

  def predict(jobId: JobId, predictRequest: PredictRequest): Future[(PredictionResult, JobTimeInfo)] = {
    log.info("[predict] starting predict process")
    val result = for {
      //importing table from external database as csv to s3
      (inputDataSource, outputDataSource) <- Try {
        val input = predictRequest.input.getOrElse(throw new CortexException("input datasource is empty"))
        val output = predictRequest.output.getOrElse(throw new CortexException("output datasource is empty"))
        (input, output)
      }.toFuture
      inputTablePath = s"$tmpDirPath/tables/$jobId/input.csv"
      importTaskResult <- {
        log.info("[predict] starting import from database")
        importFromDatabase(jobId, inputDataSource, inputTablePath)
      }

      //starting predict process
      (predictResult, predictJobTasksTimeInfo) <- {
        val columnMappings = predictRequest.predictors.map(cm => (cm.currentName, cm.trainName)).toMap
        val predictParams = TabularJobPredictParams(
          inputDataPaths      = Seq(importTaskResult.outputS3Path),
          modelId             = predictRequest.modelId,
          columnsMapping      = columnMappings,
          predictColName      = predictRequest.predictionResultColumnName,
          probabilityColNames = predictRequest.probabilityColumns.map {
            case ProbabilityClassColumn(className, columnName) => className -> columnName
          }.toMap,
          classReference      = fromCortexClassReference(
            predictRequest.modelReference.getOrElse(throw new CortexException("model class reference isn't defined"))
          )
        )
        log.info("[predict] starting predict")
        tabularJob.predict(jobId, predictParams)
      }

      //saving a result to external system
      outputTableColumns <- getOutputColumns(
        inputDataSource      = inputDataSource,
        responseType         = predictResult.responseColumnType,
        predictionColumnName = predictRequest.predictionResultColumnName,
        probabilityColNames  = predictRequest.probabilityColumns.map(_.columnName),
        headerColNames       = predictResult.headerColNames
      ).toFuture
      exportJobTasksTimeInfo <- {
        log.info("[predict] starting export to database")
        exportToDatabase(
          jobId      = jobId,
          dataSource = outputDataSource,
          columns    = outputTableColumns,
          s3SrcPath  = predictResult.predictPath,
          dropTable  = predictRequest.dropPreviousResultTable
        )
      }
    } yield {
      (PredictionResult(
        classProbabilityColumnNames = predictRequest.probabilityColumns.map(_.columnName),
        output                      = Some(outputDataSource)
      ), JobTimeInfo(Seq(importTaskResult.taskTimeInfo) ++ predictJobTasksTimeInfo ++ exportJobTasksTimeInfo))
    }

    result
  }

  def evaluate(jobId: JobId, evaluateRequest: EvaluateRequest): Future[(EvaluationResult, JobTimeInfo)] = {
    log.info("[evaluate] starting evaluate process")
    val result = for {
      //importing table from external database as csv to s3
      (inputDataSource, outputDataSource) <- Try {
        val input = evaluateRequest.input.getOrElse(throw new CortexException("input datasource is empty"))
        val output = evaluateRequest.output.getOrElse(throw new CortexException("output datasource is empty"))
        (input, output)
      }.toFuture
      inputTablePath = s"$tmpDirPath/tables/$jobId/input.csv"
      importTaskResult <- {
        log.info("[evaluate] starting import from database")
        importFromDatabase(jobId, inputDataSource, inputTablePath)
      }

      //starting evaluate process
      (evaluateResult, evaluateJobTasksTimeInfo) <- {
        val columnMappings = evaluateRequest.predictors.map(cm => (cm.currentName, cm.trainName)).toMap ++
          evaluateRequest.weight.map(cm => (cm.currentName, cm.trainName)).toSeq.toMap ++
          evaluateRequest.response.map(cm => (cm.currentName, cm.trainName)).toSeq.toMap
        val evaluateParams = TabularJobEvaluateParams(
          inputDataPaths      = Seq(importTaskResult.outputS3Path),
          modelId             = evaluateRequest.modelId,
          columnsMapping      = columnMappings,
          predictColName      = evaluateRequest.predictionResultColumnName,
          probabilityColNames = evaluateRequest.probabilityColumns.map {
            case ProbabilityClassColumn(className, columnName) => className -> columnName
          }.toMap,
          weightsCol          = evaluateRequest.weight.map(_.trainName),
          classReference      = fromCortexClassReference(
            evaluateRequest.modelReference.getOrElse(throw new CortexException("model class reference isn't defined"))
          )
        )
        log.info("[evaluate] starting evaluate")
        tabularJob.evaluate(jobId, evaluateParams)
      }

      //saving a result to external system
      outputTableColumns <- getOutputColumns(
        inputDataSource      = inputDataSource,
        responseType         = evaluateResult.responseColumnType,
        predictionColumnName = evaluateRequest.predictionResultColumnName,
        probabilityColNames  = evaluateRequest.probabilityColumns.map(_.columnName),
        headerColNames       = evaluateResult.headerColNames
      ).toFuture
      exportJobTasksTimeInfo <- {
        log.info("[evaluate] starting export to database")
        exportToDatabase(
          jobId      = jobId,
          dataSource = outputDataSource,
          columns    = outputTableColumns,
          s3SrcPath  = evaluateResult.predictPath,
          dropTable  = evaluateRequest.dropPreviousResultTable
        )
      }
    } yield {
      val summary = translateSummary(evaluateResult.summaryStats)
      //TODO fix this workaround
      val evalSummary = summary.binaryClassificationTrainSummary
        .map(x => EvaluationResult.Summary.BinaryClassificationEvalSummary(x.binaryClassificationEvalSummary
          .getOrElse(throw new RuntimeException("unexpected state"))))
        .orElse(summary.regressionSummary.map(EvaluationResult.Summary.RegressionSummary))
        .orElse(summary.classificationSummary.map(EvaluationResult.Summary.ClassificationSummary))
        .getOrElse(EvaluationResult.Summary.Empty)
      (EvaluationResult(
        output  = Some(outputDataSource),
        summary = evalSummary
      ), JobTimeInfo(Seq(importTaskResult.taskTimeInfo) ++ evaluateJobTasksTimeInfo ++ exportJobTasksTimeInfo))
    }

    result
  }

  private def getOutputColumns(
    inputDataSource:      DataSource,
    responseType:         ColumnDataType,
    predictionColumnName: String,
    probabilityColNames:  Seq[String],
    headerColNames:       Seq[String]
  ): Try[Seq[Column]] = Try {
    val inputTable = inputDataSource.table.getOrElse(throw new CortexException("input table is empty"))
    val inputColumnsMap =
      inputTable.columns.map(x => (x.name, Column(x.name, TableConverters.apiDataTypeToDomain(x.dataType)))).toMap
    val probColNamesMap = probabilityColNames.map(x => (x, Column(x, ColumnDataType.DOUBLE))).toMap
    val outputColumnsMapper = {
      inputColumnsMap ++
        //prediction result should have the same type as a response
        Map(predictionColumnName -> Column(predictionColumnName, responseType)) ++
        //assume that all prob columns have double type
        probColNamesMap
    }
    headerColNames.map(x => outputColumnsMapper
      .getOrElse(x, throw new CortexException(s"can't find column $x in mapper " +
        s"header columns: ${headerColNames.mkString(",")} |" +
        s"mapper columns: ${outputColumnsMapper.keys.toList.mkString(",")}")))
  }

  private def translateSummary(summary: TabularModelSummary) = {
    def confusionClasses(confusionMatrix: Seq[Int], labels: Seq[String]) = {
      val classConfusionSeq = confusionMatrix.grouped(4).toList.zip(labels).map {
        case (group, label) =>
          val Seq(tp, fp, fn, tn) = group
          ClassConfusion(label, tp, tn, fp, fn)
      }
      classConfusionSeq
    }

    summary match {
      case TabularClassifierSummary(confusionMatrix, labels, None, _, _) =>
        val classConfusionSeq: List[ClassConfusion] = confusionClasses(confusionMatrix, labels)
        TrainResult.Summary.ClassificationSummary(ClassificationSummary(classConfusionSeq))

      case TabularClassifierSummary(confusionMatrix, labels, Some(TabularBinaryClassifierSummary(Some(trainSummary), ksStats)), _, _) =>
        val classConfusionSeq: List[ClassConfusion] = confusionClasses(confusionMatrix, labels)
        val generalClassificationSummary = ClassificationSummary(classConfusionSeq)
        val evalSummary = BinaryClassificationEvalSummary(
          generalClassificationSummary = Some(generalClassificationSummary),
          ks                           = ksStats
        )
        val binaryClassificationTrainSummary =
          BinaryClassificationTrainSummary(
            areaUnderRoc                    = trainSummary.auc,
            rocValues                       = trainSummary.rocFpr.zip(trainSummary.rocTpr).map { case (f, t) => RocValue(f, t) },
            f1Score                         = trainSummary.f1Score,
            precision                       = trainSummary.precision,
            recall                          = trainSummary.recall,
            threshold                       = trainSummary.threshold,
            binaryClassificationEvalSummary = Some(evalSummary)
          )
        TrainResult.Summary.BinaryClassificationTrainSummary(binaryClassificationTrainSummary)

      case TabularClassifierSummary(confusionMatrix, labels, Some(TabularBinaryClassifierSummary(None, ksStats)), _, _) =>
        val classConfusionSeq: List[ClassConfusion] = confusionClasses(confusionMatrix, labels)
        val generalClassificationSummary = ClassificationSummary(classConfusionSeq)
        val evalSummary = BinaryClassificationEvalSummary(
          generalClassificationSummary = Some(generalClassificationSummary),
          ks                           = ksStats
        )
        TrainResult.Summary.BinaryClassificationTrainSummary(
          value = BinaryClassificationTrainSummary(binaryClassificationEvalSummary = Some(evalSummary))
        )

      case TabularRegressorSummary(rmse, rSquared, mape, _, _) =>
        TrainResult.Summary.RegressionSummary(RegressionSummary(rmse, rSquared, mape))
    }
  }

  protected def getPredictorsSummary(summary: TabularModelSummary): Seq[PredictorSummary] = {
    summary.variableInfo.fold(Seq.empty[PredictorSummary]) { variablesInfo =>
      variablesInfo.map(vi => {
        //TODO tabular python side refactoring
        if (vi.valueType.toLowerCase.contains("importance")) {
          val treeSummary = PredictorSummary.Summary.TreeModelPredictorSummary(TreeModelPredictorSummary(vi.value))
          PredictorSummary(vi.variableName, treeSummary)
        } else {
          var pps = ParametricModelPredictorSummary(coefficient = vi.value)
          pps = vi.stderr.fold(pps)(pps.withStdErr)
          pps = vi.pvalue.fold(pps)(pps.withPValue)
          pps = vi.tvalue.fold(pps)(pps.withTValue)
          val parametricSummary = PredictorSummary.Summary.ParametricModelPredictorSummary(pps)
          PredictorSummary(vi.variableName, parametricSummary)
        }
      })
    }
  }

  private def importFromDatabase(
    jobId:      String,
    dataSource: DataSource,
    destPath:   String
  ) = {
    for {
      importResult <- importerJob.importFromTable(
        jobId,
        table    = TableConverters.apiDataSourceToTable(dataSource),
        destPath = destPath
      )
    } yield importResult
  }

  private def exportToDatabase(
    jobId:      String,
    dataSource: DataSource,
    columns:    Seq[Column],
    s3SrcPath:  String,
    dropTable:  Boolean
  ) = {
    //TODO until python code won't stop placing dots for int values in csv output (1.0 instead of 1)
    val updatedColumns = columns.map { x =>
      x.`type` match {
        case ColumnDataType.LONG | ColumnDataType.INTEGER => x.copy(`type` = ColumnDataType.DOUBLE)
        case _ => x
      }
    }

    for {
      exportResult <- exporterJob.exportToTable(
        jobId    = jobId,
        table    = TableConverters.apiDataSourceToTable(dataSource),
        srcPath  = s3SrcPath,
        columns  = updatedColumns,
        fileType = TableFileType.CSV
      )
    } yield Seq(exportResult.taskTimeInfo)
  }

  private def getModelType(summary: TabularModelSummary): ModelType = summary match {
    case TabularModelSummary.TabularClassifierSummary(_, labels, _, _, _) =>
      if (labels.size <= 2) {
        ModelType.BINARY
      } else {
        ModelType.MULTICLASS
      }
    case TabularModelSummary.TabularRegressorSummary(_, _, _, _, _) => ModelType.REGRESSION
  }

  def importModel(jobId: JobId, request: TabularModelImportRequest): Future[(TabularModelImportResult, JobTimeInfo)] = {
    val modelClassReference = request.modelClassReference.getOrElse(
      throw new RuntimeException(
        "Tabular model import request doesn't contain mandatory parameter `modelClassReference`"
      )
    )
    val params = TabularModelImportTaskParams(
      classReference      = fromCortexClassReference(modelClassReference),
      modelsBasePath      = modelsBasePath,
      modelPath           = request.path,
      storageAccessParams = storageAccessParams
    )

    for {
      result <- modelImportJob.importModel(jobId, params)
    } yield {
      (
        TabularModelImportResult(
          Some(ModelReference(
            id       = result.modelReference.id,
            filePath = result.modelReference.path
          ))
        ),
          JobTimeInfo(Seq(result.taskTimeInfo))
      )
    }
  }

  override def handlePartial: PartialFunction[(JobId, JobRequest), JobResult] = {
    case (jobId, jobReq) if jobReq.`type` == TabularTrain =>
      val trainRequest = TrainRequest.parseFrom(jobReq.payload.toByteArray)
      trainPredict(jobId, trainRequest)

    case (jobId, jobReq) if jobReq.`type` == TabularPredict =>
      val predictRequest = PredictRequest.parseFrom(jobReq.payload.toByteArray)
      predict(jobId, predictRequest)

    case (jobId, jobReq) if jobReq.`type` == TabularEvaluate =>
      val evaluateRequest = EvaluateRequest.parseFrom(jobReq.payload.toByteArray)
      evaluate(jobId, evaluateRequest)

    case (jobId, jobReq) if jobReq.`type` == TabularModelImport =>
      val importRequest = TabularModelImportRequest.parseFrom(jobReq.payload.toByteArray)
      importModel(jobId, importRequest)
  }
}

object TabularDataService {

  def apply(
    scheduler:            TaskScheduler,
    s3AccessParams:       S3AccessParams,
    redshiftAccessParams: RedshiftAccessParams,
    s3client:             S3Client,
    settings:             SettingsModule
  )(implicit executionContext: ExecutionContext, loggerFactory: JMLoggerFactory): TabularDataService = {

    val modelsBasePath = settings.jobsPath

    val redshiftImporterModule = new RedshiftImporterModule(
      s3Bucket       = s3AccessParams.bucket,
      baseOutputPath = settings.jobsPath
    )
    val redshiftImporterJob = new RedshiftImporterJob(
      scheduler                 = scheduler,
      redshiftAccessParams      = redshiftAccessParams,
      s3AccessParams            = s3AccessParams,
      redshiftImporterModule    = redshiftImporterModule,
      redshiftImporterJobConfig = settings.redshiftImporterConfig
    )

    val redshiftExporterModule = new RedshiftExporterModule()
    val redshiftExporterJob = new RedshiftExporterJob(
      scheduler                 = scheduler,
      redshiftAccessParams      = redshiftAccessParams,
      s3AccessParams            = s3AccessParams,
      redshiftExporterModule    = redshiftExporterModule,
      redshiftExporterJobConfig = settings.redshiftExporterConfig
    )

    val tabularModule = new TabularPipelineModule(settings.jobsPath)
    val crossValidationJob = new CrossValidationJob(
      scheduler                = scheduler,
      tabularModule            = tabularModule,
      storageAccessParams      = s3AccessParams,
      crossValidationJobConfig = settings.crossValidationConfig
    )
    val hpRandomSearch = new HyperParamRandomSearch()
    val hpSelector = new HyperParamSelector(crossValidationJob, hpRandomSearch)
    val splitterModule = new SplitterModule(splits = 3, "output-", settings.jobsPath)
    val splitterJob = new SplitterJob(
      scheduler         = scheduler,
      splitterModule    = splitterModule,
      splitterJobConfig = settings.splitterConfig
    )
    val mVHModule = new MVHModule(modelsBasePath)
    val tabularTrainPredictJob = new TabularJob(
      scheduler             = scheduler,
      hyperParamSelector    = hpSelector,
      splitterJob           = splitterJob,
      mvhModule             = mVHModule,
      tabularPipelineModule = tabularModule,
      storageAccessParams   = s3AccessParams,
      tabularJobConfig      = settings.tabularConfig,
      modelsBasePath        = modelsBasePath,
      tmpDirPath            = settings.tmpDirPath
    )

    val modelImportModule = new TabularModelImportModule
    val modelImportJob = new TabularModelImportJob(
      scheduler,
      modelImportModule,
      settings.tabularModelImportConfig
    )

    new TabularDataService(
      tabularJob          = tabularTrainPredictJob,
      importerJob         = redshiftImporterJob,
      exporterJob         = redshiftExporterJob,
      modelImportJob      = modelImportJob,
      storageAccessParams = s3AccessParams,
      modelsBasePath      = modelsBasePath,
      tmpDirPath          = settings.tmpDirPath
    )
  }
}
