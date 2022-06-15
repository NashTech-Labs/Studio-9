package cortex.task.project_packager

import cortex.{ TaskParams, TaskResult }
import cortex.JsonSupport.SnakeJson
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.project_packager.ProjectPackagerParams.ParameterCondition._
import cortex.task.project_packager.ProjectPackagerParams.PipelineDataType._
import cortex.task.project_packager.ProjectPackagerParams.TypeInfo._
import play.api.libs.json._

object ProjectPackagerParams {
  case class ProjectPackagerTaskParams(
      projectFilesPath: String,
      packageName:      String,
      packageVersion:   String,
      packagePath:      String,
      s3Params:         S3AccessParams
  ) extends TaskParams

  case class ProjectPackagerTaskResult(
      packageLocation:     String,
      cvTlModelPrimitives: Seq[CVTLModelPrimitiveMeta],
      pipelineOperators:   Seq[PipelineOperatorMeta]
  ) extends TaskResult

  case class CVTLModelPrimitiveMeta(
      name:         String,
      description:  Option[String],
      moduleName:   String,
      className:    String,
      operatorType: String,
      params:       List[OperatorParameter],
      isNeural:     Boolean
  )

  case class PipelineOperatorMeta(
      name:        String,
      description: Option[String],
      moduleName:  String,
      className:   String,
      params:      List[OperatorParameter],
      inputs:      List[PipelineOperatorInput],
      outputs:     List[PipelineOperatorOutput]
  )

  case class PipelineOperatorInput(
      name:        String,
      description: Option[String],
      `type`:      PipelineDataType,
      covariate:   Boolean,
      required:    Boolean
  )

  case class PipelineOperatorOutput(
      description: Option[String],
      `type`:      PipelineDataType
  )

  sealed trait PipelineDataType

  object PipelineDataType {
    case object StringPrimitiveDataType extends PipelineDataType
    case object BooleanPrimitiveDataType extends PipelineDataType
    case object FloatPrimitiveDataType extends PipelineDataType
    case object IntegerPrimitiveDataType extends PipelineDataType

    case class ComplexDataType(
        definition:    String,
        parents:       Option[List[ComplexDataType]],
        typeArguments: Option[List[PipelineDataType]]
    ) extends PipelineDataType
  }

  case class OperatorParameter(
      name:        String,
      description: Option[String],
      multiple:    Boolean,
      typeInfo:    TypeInfo,
      conditions:  Map[String, ParameterCondition]
  )

  sealed trait TypeInfo

  object TypeInfo {
    case class StringTypeInfo(
        values:  Seq[String],
        default: Seq[String]
    ) extends TypeInfo
    case class IntTypeInfo(
        values:  Seq[Int],
        default: Seq[Int],
        max:     Option[Int],
        min:     Option[Int],
        step:    Option[Int]
    ) extends TypeInfo
    case class FloatTypeInfo(
        values:  Seq[Float],
        default: Seq[Float],
        max:     Option[Float],
        min:     Option[Float],
        step:    Option[Float]
    ) extends TypeInfo
    case class BooleanTypeInfo(
        default: Seq[Boolean]
    ) extends TypeInfo
    case class AssetTypeInfo(
        assetType: AssetType
    ) extends TypeInfo
  }

  sealed trait ParameterCondition

  object ParameterCondition {
    case class StringParameterCondition(
        values: Seq[String]
    ) extends ParameterCondition
    case class IntParameterCondition(
        values: Seq[Int],
        max:    Option[Int],
        min:    Option[Int]
    ) extends ParameterCondition
    case class FloatParameterCondition(
        values: Seq[Float],
        max:    Option[Float],
        min:    Option[Float]
    ) extends ParameterCondition
    case class BooleanParameterCondition(
        value: Boolean
    ) extends ParameterCondition
  }

  implicit object PipelineDataTypeReads extends Reads[PipelineDataType] {
    private implicit val complexDataTypeReads: Reads[ComplexDataType] = SnakeJson.reads[ComplexDataType]

    override def reads(json: JsValue): JsResult[PipelineDataType] = json match {
      case JsString(primitiveDataType) => primitiveDataType match {
        case "String"  => JsSuccess(StringPrimitiveDataType)
        case "Integer" => JsSuccess(IntegerPrimitiveDataType)
        case "Float"   => JsSuccess(FloatPrimitiveDataType)
        case "Boolean" => JsSuccess(BooleanPrimitiveDataType)
        case _         => JsError(s"Invalid PrimitiveDataType: $json. One of [String, Integer, Float, Boolean] is expected")
      }
      case _ if json.validate[ComplexDataType].isSuccess => complexDataTypeReads.reads(json)
      case _ => JsError(s"Invalid PipelineDataType: $json")
    }
  }

  implicit object TypeInfoReads extends Reads[TypeInfo] {
    private val stringTypeInfoReads: Reads[StringTypeInfo] = SnakeJson.reads[StringTypeInfo]
    private val intTypeInfoReads: Reads[IntTypeInfo] = SnakeJson.reads[IntTypeInfo]
    private val floatTypeInfoReads: Reads[FloatTypeInfo] = SnakeJson.reads[FloatTypeInfo]
    private val booleanTypeInfoReads: Reads[BooleanTypeInfo] = SnakeJson.reads[BooleanTypeInfo]
    private val assetTypeInfoReads: Reads[AssetTypeInfo] = SnakeJson.reads[AssetTypeInfo]

    override def reads(json: JsValue): JsResult[TypeInfo] = json \ "type_hint" match {
      case JsDefined(JsString(typeHint)) => typeHint match {
        case "str"   => stringTypeInfoReads.reads(json)
        case "int"   => intTypeInfoReads.reads(json)
        case "float" => floatTypeInfoReads.reads(json)
        case "bool"  => booleanTypeInfoReads.reads(json)
        case "asset" => assetTypeInfoReads.reads(json)
        case _       => JsError(s"Invalid TypeInfo: $json")
      }
      case JsDefined(_)   => JsError("Expected json string for type_hint field")
      case _: JsUndefined => JsError(s"Invalid TypeInfo: $json. TypeInfo is required")
    }
  }

  implicit object ParameterConditionReads extends Reads[ParameterCondition] {
    private val stringParameterConditionReads: Reads[StringParameterCondition] = SnakeJson.reads[StringParameterCondition]
    private val intParameterConditionReads: Reads[IntParameterCondition] = SnakeJson.reads[IntParameterCondition]
    private val floatParameterConditionReads: Reads[FloatParameterCondition] = SnakeJson.reads[FloatParameterCondition]

    override def reads(json: JsValue): JsResult[ParameterCondition] = (json \ "values", json \ "value") match {
      case (JsDefined(JsArray(values)), _) =>
        if (values.nonEmpty) {
          if (values.forall(_.validate[String].isSuccess)) {
            stringParameterConditionReads.reads(json)
          } else if (values.forall(_.validate[Int].isSuccess)) {
            intParameterConditionReads.reads(json)
          } else {
            if (values.forall(_.validate[Float].isSuccess)) {
              floatParameterConditionReads.reads(json)
            } else {
              JsError(s"Invalid ParameterCondition: $json")
            }
          }
        } else {
          JsError(s"Invalid ParameterCondition: $json")
        }
      case (JsDefined(_), _) => JsError("Expected json array for values field")
      case (_: JsUndefined, JsDefined(JsBoolean(value))) => JsSuccess(BooleanParameterCondition(value))
      case _ => JsError(s"Invalid ParameterCondition: $json. ParameterCondition is required")
    }
  }

  implicit val projectPackagerTaskParamsWrites: OWrites[ProjectPackagerTaskParams] = SnakeJson.writes[ProjectPackagerTaskParams]
  private implicit val operatorParameterReads: Reads[OperatorParameter] = SnakeJson.reads[OperatorParameter]
  private implicit val cVTLModelPrimitiveMetaReads: Reads[CVTLModelPrimitiveMeta] = SnakeJson.reads[CVTLModelPrimitiveMeta]
  private implicit val pipelineOperatorInputReads: Reads[PipelineOperatorInput] = SnakeJson.reads[PipelineOperatorInput]
  private implicit val pipelineOperatorOutputReads: Reads[PipelineOperatorOutput] = SnakeJson.reads[PipelineOperatorOutput]
  private implicit val pipelineOperatorMetaReads: Reads[PipelineOperatorMeta] = SnakeJson.reads[PipelineOperatorMeta]
  implicit val projectPackagerTaskResultReads: Reads[ProjectPackagerTaskResult] = SnakeJson.reads[ProjectPackagerTaskResult]
}
