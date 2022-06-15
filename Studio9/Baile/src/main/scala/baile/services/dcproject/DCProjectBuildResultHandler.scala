package baile.services.dcproject

import java.time.Instant
import java.util.UUID

import baile.dao.cv.model.tlprimitives.CVTLModelPrimitiveDao
import baile.dao.dcproject.DCProjectPackageDao.{ NameIs, OwnerIdIs }
import baile.dao.dcproject.{ DCProjectDao, DCProjectPackageDao }
import baile.dao.pipeline.PipelineOperatorDao
import baile.dao.pipeline.PipelineOperatorDao.{ ClassNameIs, ModuleNameIs, PackageIdIn }
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.common.Version
import baile.domain.cv.model.tlprimitives._
import baile.domain.dcproject.{ DCProjectPackage, DCProjectStatus }
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.domain.pipeline.{ PipelineOperator, _ }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.dcproject.DCProjectBuildResultHandler.Meta
import baile.services.process.JobResultHandler
import baile.utils.TryExtensions._
import cortex.api.job.project.`package`.OperatorParameter.TypeInfo
import cortex.api.job.project.`package`.ParameterCondition.Condition
import cortex.api.job.project.`package`.{
  ProjectPackageResponse,
  AssetParameter => CortexAssetParameter,
  AssetType => CortexAssetType,
  BooleanParameter => CortexBooleanParameter,
  BooleanParameterCondition => CortexBooleanParameterCondition,
  CVTLModelPrimitive => CortexCVTLModelPrimitive,
  ComplexDataType => CortexComplexDataType,
  FloatParameter => CortexFloatParameter,
  FloatParameterCondition => CortexFloatParameterCondition,
  IntParameter => CortexIntParameter,
  IntParameterCondition => CortexIntParameterCondition,
  OperatorParameter => CortexOperatorParameter,
  OperatorType => CortexOperatorType,
  ParameterCondition => CortexParameterCondition,
  PipelineDataType => CortexPipelineDataType,
  PipelineOperator => CortexPipelineOperator,
  PipelineOperatorInput => CortexPipelineOperatorInput,
  PipelineOperatorOutput => CortexPipelineOperatorOutput,
  PrimitiveDataType => CortexPrimitiveDataType,
  StringParameter => CortexStringParameter,
  StringParameterCondition => CortexStringParameterCondition
}
import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class DCProjectBuildResultHandler(
  dcProjectDao: DCProjectDao,
  dCProjectPackageDao: DCProjectPackageDao,
  cvModelTLPrimitiveDao: CVTLModelPrimitiveDao,
  pipelineOperatorDao: PipelineOperatorDao,
  cortexJobService: CortexJobService,
  jobMetaService: JobMetaService
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] = DCProjectBuildResultHandler.DCProjectBuildResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] = {

    def storeCVTLModelPrimitivesIfPublished(
      cortexCVTLModelPrimitives: Seq[CortexCVTLModelPrimitive],
      packageId: String
    ): Future[Unit] = {
      if (meta.analyzePipelineOperators) {
        for {
          cvtlModelPrimitives <- Try(cortexCVTLModelPrimitives.map(
            convertCortexCVTLModelPrimitiveToCVTLModelPrimitive(packageId, _)
          )).toFuture
          _ <- cvModelTLPrimitiveDao.createMany(cvtlModelPrimitives)
        } yield ()
      } else {
        Future.unit
      }
    }

    def storePipelineOperatorsIfPublished(
      cortexPipelineOperators: Seq[CortexPipelineOperator],
      packageId: String
    ): Future[Unit] = {
      if (meta.analyzePipelineOperators) {
        for {
          oldPackages <- dCProjectPackageDao.listAll(OwnerIdIs(meta.userId) && NameIs(meta.name))
          oldPackageVersions = oldPackages
            .foldLeft(Map.empty[String, Option[Version]]) { case (soFar, WithId(dcPackage, dcPackageId)) =>
              soFar + (dcPackageId -> dcPackage.version)
            }
          pipelineOperators <- Future.sequence(cortexPipelineOperators.map(
            buildPipelineOperator(packageId, oldPackageVersions, _)
          ))
          _ <- pipelineOperatorDao.createMany(pipelineOperators)
        } yield ()
      } else {
        Future.unit
      }
    }

    lastStatus match {
      case CortexJobStatus.Completed =>
        for {
          outputPath <- cortexJobService.getJobOutputPath(jobId)
          rawJobResult <- jobMetaService.readRawMeta(jobId, outputPath)
          result <- Try(ProjectPackageResponse.parseFrom(rawJobResult)).toFuture
          packageId <- dCProjectPackageDao.create(
            DCProjectPackage(
              ownerId = Some(meta.userId),
              dcProjectId = Some(meta.dcProjectId),
              name = meta.name,
              location = Some(result.packageLocation),
              version = Some(meta.version),
              created = Instant.now(),
              description = meta.description,
              isPublished = false
            )
          )
          _ <- storeCVTLModelPrimitivesIfPublished(result.cvTlModelPrimitives, packageId)
          _ <- storePipelineOperatorsIfPublished(result.pipelineOperators, packageId)
          _ <- dcProjectDao.update(
            meta.dcProjectId,
            _.copy(
              status = DCProjectStatus.Idle,
              latestPackageVersion = Some(meta.version)
            )
          )
        } yield ()
      case CortexJobStatus.Cancelled | CortexJobStatus.Failed =>
        handleException(meta)
    }

  }

  override protected def handleException(meta: Meta): Future[Unit] = {
    dcProjectDao.update(
      meta.dcProjectId,
      project => {
        val packageName = if (meta.packageAlreadyExists) project.packageName else None
        project.copy(status = DCProjectStatus.Idle, packageName = packageName)
      }
    ).map(_ => ())
  }

  private def buildPipelineOperator(
    packageId: String,
    oldPackageVersions: Map[String, Option[Version]],
    cortexPipelineOperator: CortexPipelineOperator
  ): Future[PipelineOperator] =
    for {
      oldOperators <- pipelineOperatorDao.listAll(
        PackageIdIn(oldPackageVersions.keys.toSeq) &&
        ModuleNameIs(cortexPipelineOperator.moduleName) &&
        ClassNameIs(cortexPipelineOperator.className)
      )
      oldOperator = oldOperators
        .sortBy(operator => oldPackageVersions(operator.entity.packageId))
        .headOption
    } yield PipelineOperator(
      name = oldOperator.fold(cortexPipelineOperator.name)(_.entity.name),
      description = cortexPipelineOperator.description,
      category = oldOperator.fold[Option[String]](None)(_.entity.category),
      className = cortexPipelineOperator.className,
      moduleName = cortexPipelineOperator.moduleName,
      packageId = packageId,
      inputs = cortexPipelineOperator.inputs.map(convertCortexPipelineOperatorInputToPipelineOperatorInput),
      outputs = cortexPipelineOperator.outputs.map(convertCortexPipelineOperatorOutputToPipelineOperatorOutput),
      params = cortexPipelineOperator.params.map(convertCortexOperatorParameterToOperatorParameter)
    )

  private def convertCortexPipelineOperatorInputToPipelineOperatorInput(
    cortexPipelineOperatorInput: CortexPipelineOperatorInput
  ): PipelineOperatorInput = {
    PipelineOperatorInput(
      name = cortexPipelineOperatorInput.name,
      description = cortexPipelineOperatorInput.description,
      `type` = convertCortexPipelineDataTypeToPipelineDataType(cortexPipelineOperatorInput.`type`.get),
      covariate = cortexPipelineOperatorInput.covariate,
      required = cortexPipelineOperatorInput.required
    )
  }

  private def convertCortexPipelineOperatorOutputToPipelineOperatorOutput(
    cortexPipelineOperatorOutput: CortexPipelineOperatorOutput
  ): PipelineOperatorOutput = {
    PipelineOperatorOutput(
      description = cortexPipelineOperatorOutput.description,
      `type` = convertCortexPipelineDataTypeToPipelineDataType(cortexPipelineOperatorOutput.`type`.get)
    )
  }

  def convertCortexPipelineDataTypeToPipelineDataType(
    cortexPipelineDataType: CortexPipelineDataType
  ): PipelineDataType = {
    cortexPipelineDataType.dataType match {
      case CortexPipelineDataType.DataType.PrimitiveDataType(value) => value match {
        case CortexPrimitiveDataType.Integer => PrimitiveDataType.Integer
        case CortexPrimitiveDataType.String => PrimitiveDataType.String
        case CortexPrimitiveDataType.Boolean => PrimitiveDataType.Boolean
        case CortexPrimitiveDataType.Float => PrimitiveDataType.Float
        case _ => throw new RuntimeException("Invalid DataType")
      }
      case CortexPipelineDataType.DataType.ComplexDataType(values) =>
        cortexComplexDataTypeToComplexDataType(values.definition, values.parents, values.typeArguments)
      case _ => throw new RuntimeException("No DataType found")
    }
  }

  def cortexComplexDataTypeToComplexDataType(
    definition: String,
    parents: Seq[CortexComplexDataType],
    typeArguments: Seq[CortexPipelineDataType]
  ): ComplexDataType = ComplexDataType(
    definition,
    parents.map(value =>
      cortexComplexDataTypeToComplexDataType(value.definition, value.parents, value.typeArguments)
    ),
    typeArguments.map(convertCortexPipelineDataTypeToPipelineDataType)
  )

  def cortexAssetParameterToAssetParameterTypeInfo(
    asset: CortexAssetParameter
  ): AssetParameterTypeInfo = {
    asset.assetType match {
      case CortexAssetType.Album => AssetParameterTypeInfo(AssetType.Album)
      case CortexAssetType.CvModel => AssetParameterTypeInfo(AssetType.CvModel)
      case CortexAssetType.CvPrediction => AssetParameterTypeInfo(AssetType.CvPrediction)
      case CortexAssetType.DCProject => AssetParameterTypeInfo(AssetType.DCProject)
      case CortexAssetType.Flow => AssetParameterTypeInfo(AssetType.Flow)
      case CortexAssetType.OnlineJob => AssetParameterTypeInfo(AssetType.OnlineJob)
      case CortexAssetType.Table => AssetParameterTypeInfo(AssetType.Table)
      case CortexAssetType.TabularModel => AssetParameterTypeInfo(AssetType.TabularModel)
      case CortexAssetType.TabularPrediction => AssetParameterTypeInfo(AssetType.TabularPrediction)
      case _ => throw new RuntimeException("Invalid AssetType")
    }
  }

  private def convertCortexCVTLModelPrimitiveToCVTLModelPrimitive(
    id: String,
    cortexCVTLModelPrimitive: CortexCVTLModelPrimitive
  ): CVTLModelPrimitive = {
    CVTLModelPrimitive(
      packageId = id,
      name = cortexCVTLModelPrimitive.name,
      description = cortexCVTLModelPrimitive.description,
      moduleName = cortexCVTLModelPrimitive.moduleName,
      className = cortexCVTLModelPrimitive.className,
      cvTLModelPrimitiveType = cortexCVTLModelPrimitive.`type` match {
        case CortexOperatorType.Classifier => CVTLModelPrimitiveType.Classifier
        case CortexOperatorType.UTLP => CVTLModelPrimitiveType.UTLP
        case CortexOperatorType.Detector => CVTLModelPrimitiveType.Detector
        case CortexOperatorType.Unrecognized(operatorType) =>
          throw new RuntimeException(s"Unexpected operator type $operatorType found")
      },
      params = cortexCVTLModelPrimitive.params.map(convertCortexOperatorParameterToOperatorParameter),
      isNeural = cortexCVTLModelPrimitive.isNeural
    )
  }

  private def convertCortexOperatorParameterToOperatorParameter(
    cortexOperatorParameter: CortexOperatorParameter
  ): OperatorParameter = {
    OperatorParameter(
      name = cortexOperatorParameter.name,
      description = cortexOperatorParameter.description,
      multiple = cortexOperatorParameter.multiple,
      typeInfo = convertCortexTypeInfoToTypeInfo(cortexOperatorParameter.typeInfo),
      conditions = cortexOperatorParameter.conditions.mapValues(convertCortexParameterConditionToParameterCondition)
    )
  }

  private def convertCortexParameterConditionToParameterCondition(
    cortexParameterCondition: CortexParameterCondition
  ): ParameterCondition = {
    cortexParameterCondition.condition match {
      case Condition.BooleanCondition(CortexBooleanParameterCondition(value)) => BooleanParameterCondition(value)
      case Condition.FloatCondition(CortexFloatParameterCondition(values, min, max)) => FloatParameterCondition(
        values = values,
        min = min,
        max = max
      )
      case Condition.StringCondition(CortexStringParameterCondition(values)) => StringParameterCondition(
        values = values
      )
      case Condition.IntCondition(CortexIntParameterCondition(values, min, max)) => IntParameterCondition(
        values = values,
        min = min,
        max = max
      )
      case Condition.Empty => throw new RuntimeException("Invalid parameter condition")
    }
  }

  private def convertCortexTypeInfoToTypeInfo(cortexTypeInfo: TypeInfo): ParameterTypeInfo = {
    cortexTypeInfo match {
      case TypeInfo.BooleanInfo(CortexBooleanParameter(default)) => BooleanParameterTypeInfo(default)
      case TypeInfo.FloatInfo(CortexFloatParameter(values, default, min, max, step)) => FloatParameterTypeInfo(
        values = values,
        default = default,
        min = min,
        max = max,
        step = step
      )
      case TypeInfo.StringInfo(CortexStringParameter(values, default)) => StringParameterTypeInfo(
        values = values,
        default = default
      )
      case TypeInfo.IntInfo(CortexIntParameter(values, default, min, max, step)) => IntParameterTypeInfo(
        values = values,
        default = default,
        min = min,
        max = max,
        step = step
      )
      case TypeInfo.AssetInfo(assetParameter: CortexAssetParameter) =>
        cortexAssetParameterToAssetParameterTypeInfo(assetParameter)
      case TypeInfo.Empty => throw new RuntimeException("Invalid type information")
    }
  }

}

object DCProjectBuildResultHandler {

  case class Meta(
    dcProjectId: String,
    name: String,
    version: Version,
    userId: UUID,
    packageAlreadyExists: Boolean,
    description: Option[String],
    analyzePipelineOperators: Boolean
  )

  implicit val VersionMetaFormat: Format[Version] = {
    val reads = for {
      str <- Reads.of[String]
      result <- Version.parseFrom(str) match {
        case Some(Version(major, minor, patch, suffix)) => Reads.pure[Version](Version(major, minor, patch, suffix))
        case None => Reads[Version](_ => JsError("Invalid version format"))
      }
    } yield result
    val writes = Writes.of[String].contramap[Version](_.toString)
    Format(reads, writes)
  }

  implicit val DCProjectBuildResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]

}
