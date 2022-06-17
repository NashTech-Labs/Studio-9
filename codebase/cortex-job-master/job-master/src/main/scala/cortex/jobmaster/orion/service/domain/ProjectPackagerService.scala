package cortex.jobmaster.orion.service.domain

import cortex.api.job.{ JobRequest, JobType }
import cortex.api.job.project.`package`.{
  OperatorParameter => CortexOperatorParameter,
  ParameterCondition => CortexParameterCondition,
  PipelineDataType => CortexPipelineDataType,
  PipelineOperatorInput => CortexPipelineOperatorInput,
  PipelineOperatorOutput => CortexPipelineOperatorOutput,
  PrimitiveDataType => CortexPrimitiveDataType,
  AssetType => CortexAssetType,
  ComplexDataType => CortexComplexDataType,
  _
}
import cortex.jobmaster.jobs.job.project_packager.ProjectPackagerJob
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler.{ JobId, JobResult }
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.project_packager.{ AssetType, ProjectPackagerModule }
import cortex.task.project_packager.ProjectPackagerParams._

import scala.concurrent.{ ExecutionContext, Future }

class ProjectPackagerService(job: ProjectPackagerJob)(implicit executionContext: ExecutionContext)
  extends JobRequestPartialHandler {

  private def toCVTLModelPrimitive(from: CVTLModelPrimitiveMeta): CVTLModelPrimitive = {
    CVTLModelPrimitive(
      name        = from.name,
      description = from.description,
      moduleName  = from.moduleName,
      className   = from.className,
      `type`      = parseOperatorType(from.operatorType),
      params      = from.params.map(toOperatorParameter),
      isNeural    = from.isNeural
    )
  }

  private def parseOperatorType(operatorType: String) = {
    operatorType match {
      case "UTLP"       => OperatorType.UTLP
      case "Classifier" => OperatorType.Classifier
      case "Detector"   => OperatorType.Detector
      case _            => throw new RuntimeException("Unrecognized OperatorType")
    }
  }

  private def toPipelineOperator(from: PipelineOperatorMeta): PipelineOperator = {
    PipelineOperator(
      name        = from.name,
      description = from.description,
      moduleName  = from.moduleName,
      className   = from.className,
      params      = from.params.map(toOperatorParameter),
      inputs      = from.inputs.map(toPipelineOperatorInput),
      outputs     = from.outputs.map(toPipelineOperatorOutput)
    )
  }

  private def toPipelineOperatorInput(from: PipelineOperatorInput): CortexPipelineOperatorInput = {
    CortexPipelineOperatorInput(
      name        = from.name,
      description = from.description,
      `type`      = Some(parsePipelineDataType(from.`type`)),
      covariate   = from.covariate,
      required    = from.required
    )
  }

  private def toPipelineOperatorOutput(from: PipelineOperatorOutput): CortexPipelineOperatorOutput = {
    CortexPipelineOperatorOutput(
      description = from.description,
      `type`      = Some(parsePipelineDataType(from.`type`))
    )
  }

  private def parsePipelineDataType(dataType: PipelineDataType): CortexPipelineDataType = {
    CortexPipelineDataType(
      dataType match {
        case PipelineDataType.StringPrimitiveDataType =>
          CortexPipelineDataType.DataType.PrimitiveDataType(CortexPrimitiveDataType.String)
        case PipelineDataType.BooleanPrimitiveDataType =>
          CortexPipelineDataType.DataType.PrimitiveDataType(CortexPrimitiveDataType.Boolean)
        case PipelineDataType.FloatPrimitiveDataType =>
          CortexPipelineDataType.DataType.PrimitiveDataType(CortexPrimitiveDataType.Float)
        case PipelineDataType.IntegerPrimitiveDataType =>
          CortexPipelineDataType.DataType.PrimitiveDataType(CortexPrimitiveDataType.Integer)
        case complexDataType: PipelineDataType.ComplexDataType =>
          CortexPipelineDataType.DataType.ComplexDataType(parseComplexDataType(complexDataType))
      }
    )
  }

  private def parseComplexDataType(complexDataType: PipelineDataType.ComplexDataType): CortexComplexDataType = {
    CortexComplexDataType(
      definition    = complexDataType.definition,
      parents       = complexDataType.parents.map(_.map(parseComplexDataType))
        .getOrElse(List.empty[CortexComplexDataType]),
      typeArguments = complexDataType.typeArguments.map(_.map(parsePipelineDataType))
        .getOrElse(List.empty[CortexPipelineDataType])
    )
  }

  private def toOperatorParameter(from: OperatorParameter): CortexOperatorParameter = {
    CortexOperatorParameter(
      name        = from.name,
      description = from.description,
      multiple    = from.multiple,
      typeInfo    = parseTypeInfo(from.typeInfo),
      conditions  = from.conditions.mapValues(parseParameterCondition)
    )
  }

  private def parseTypeInfo(typeInfo: TypeInfo) = {
    typeInfo match {
      case TypeInfo.StringTypeInfo(values, default) =>
        CortexOperatorParameter.TypeInfo.StringInfo(StringParameter(values, default))
      case TypeInfo.IntTypeInfo(values, default, max, min, step) =>
        CortexOperatorParameter.TypeInfo.IntInfo(IntParameter(values, default, min, max, step))
      case TypeInfo.FloatTypeInfo(values, default, max, min, step) =>
        CortexOperatorParameter.TypeInfo.FloatInfo(FloatParameter(values, default, min, max, step))
      case TypeInfo.BooleanTypeInfo(default) =>
        CortexOperatorParameter.TypeInfo.BooleanInfo(BooleanParameter(default))
      case TypeInfo.AssetTypeInfo(assetType) =>
        CortexOperatorParameter.TypeInfo.AssetInfo(AssetParameter(parseAssetType(assetType)))
    }
  }

  private def parseAssetType(assetType: AssetType) = {
    assetType match {
      case AssetType.TabularModel      => CortexAssetType.TabularModel
      case AssetType.CvModel           => CortexAssetType.CvModel
      case AssetType.Album             => CortexAssetType.Album
      case AssetType.Flow              => CortexAssetType.Flow
      case AssetType.Table             => CortexAssetType.Table
      case AssetType.TabularPrediction => CortexAssetType.TabularPrediction
      case AssetType.CvPrediction      => CortexAssetType.CvPrediction
      case AssetType.DCProject         => CortexAssetType.DCProject
      case AssetType.OnlineJob         => CortexAssetType.OnlineJob
      case _                           => throw new RuntimeException("Unrecognized AssetType")
    }
  }

  private def parseParameterCondition(parameterCondition: ParameterCondition): CortexParameterCondition = {
    parameterCondition match {
      case ParameterCondition.StringParameterCondition(values) =>
        CortexParameterCondition(CortexParameterCondition.Condition.StringCondition(StringParameterCondition(values)))
      case ParameterCondition.IntParameterCondition(values, max, min) =>
        CortexParameterCondition(CortexParameterCondition.Condition.IntCondition(IntParameterCondition(values, min, max)))
      case ParameterCondition.FloatParameterCondition(values, max, min) =>
        CortexParameterCondition(CortexParameterCondition.Condition.FloatCondition(FloatParameterCondition(values, min, max)))
      case ParameterCondition.BooleanParameterCondition(value) =>
        CortexParameterCondition(CortexParameterCondition.Condition.BooleanCondition(BooleanParameterCondition(value)))
    }
  }

  def pack(jobId: JobId, request: ProjectPackageRequest): Future[(ProjectPackageResponse, JobTimeInfo)] = {

    val taskParams = ProjectPackagerTaskParams(
      projectFilesPath = request.projectFilesPath,
      packageName      = request.name,
      packageVersion   = request.version,
      packagePath      = request.targetPrefix,
      s3Params         = job.outputS3AccessParams
    )

    for {
      taskResult <- job.pack(jobId, taskParams)
    } yield {
      (ProjectPackageResponse(
        packageLocation     = taskResult.packageLocation,
        cvTlModelPrimitives = taskResult.cvTlModelPrimitives.map(toCVTLModelPrimitive),
        pipelineOperators   = taskResult.pipelineOperators.map(toPipelineOperator)
      ), JobTimeInfo(Seq(taskResult.taskTimeInfo)))
    }
  }

  override def handlePartial: PartialFunction[(JobId, JobRequest), JobResult] = {
    case (jobId, jobReq) if jobReq.`type` == JobType.ProjectPackage =>
      val projectPackageRequest = ProjectPackageRequest.parseFrom(jobReq.payload.toByteArray)
      pack(jobId, projectPackageRequest)
  }
}

object ProjectPackagerService {

  def apply(
    scheduler:      TaskScheduler,
    s3AccessParams: S3AccessParams,
    settings:       SettingsModule
  )(implicit executionContext: ExecutionContext): ProjectPackagerService = {
    val projectPackagerJob = new ProjectPackagerJob(
      scheduler                = scheduler,
      projectPackagerModule    = new ProjectPackagerModule(),
      projectPackagerJobConfig = settings.projectPackagerConfig,
      outputS3AccessParams     = s3AccessParams
    )

    new ProjectPackagerService(projectPackagerJob)
  }
}
