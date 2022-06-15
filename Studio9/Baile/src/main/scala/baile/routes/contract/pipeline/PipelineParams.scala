package baile.routes.contract.pipeline

import baile.domain.pipeline.PipelineParams.{
  BooleanParam => DomainBooleanParam,
  BooleanParams => DomainBooleanParams,
  EmptySeqParam => DomainEmptySeqParam,
  FloatParam => DomainFloatParam,
  FloatParams => DomainFloatParams,
  IntParam => DomainIntParam,
  IntParams => DomainIntParams,
  PipelineParams => DomainPipelineParams,
  StringParam => DomainStringParam,
  StringParams => DomainStringParams
}
import baile.routes.contract.pipeline.PipelineParams._
import baile.utils.json.CommonFormats
import play.api.libs.json._
import cats.implicits._
import com.iravid.playjsoncats.implicits._

case class PipelineParams(value: Map[String, PipelineParam]) extends AnyVal {
  def toDomain: DomainPipelineParams =
    value.mapValues {
      case StringParam(string) => DomainStringParam(string)
      case IntParam(int) => DomainIntParam(int)
      case FloatParam(float) => DomainFloatParam(float)
      case BooleanParam(bool) => DomainBooleanParam(bool)
      case StringParams(values) => DomainStringParams(values)
      case IntParams(values) => DomainIntParams(values)
      case FloatParams(values) => DomainFloatParams(values)
      case BooleanParams(values) => DomainBooleanParams(values)
      case EmptySeqParam => DomainEmptySeqParam
    }
}

object PipelineParams {
  sealed trait PipelineParam
  case class StringParam(value: String) extends PipelineParam
  case class IntParam(value: Int) extends PipelineParam
  case class FloatParam(value: Float) extends PipelineParam
  case class BooleanParam(value: Boolean) extends PipelineParam
  case class StringParams(values: Seq[String]) extends PipelineParam
  case class IntParams(values: Seq[Int]) extends PipelineParam
  case class FloatParams(values: Seq[Float]) extends PipelineParam
  case class BooleanParams(values: Seq[Boolean]) extends PipelineParam
  case object EmptySeqParam extends PipelineParam

  implicit val PipelineParamFormat: Format[PipelineParam] = new Format[PipelineParam] {
    override def reads(json: JsValue): JsResult[PipelineParam] = json match {
      case JsString(value) => JsSuccess(StringParam(value))
      case JsNumber(value) if value.isValidInt => JsSuccess(IntParam(value.toInt))
      case JsNumber(value) => JsSuccess(FloatParam(value.toFloat))
      case JsBoolean(value) => JsSuccess(BooleanParam(value))
      case JsArray(values) =>
        val valuesList = values.toList
        valuesList match {
          case JsString(_) :: _ =>
            multipleParamsReads(valuesList, StringParams)
          case JsBoolean(_) :: _ =>
            multipleParamsReads(valuesList, BooleanParams)
          case JsNumber(_) :: _ if (valuesList.forall(_.validate[Int].isSuccess)) =>
            multipleParamsReads(valuesList, IntParams)
          case JsNumber(_) :: _ =>
            multipleParamsReads(valuesList, FloatParams)
          case Nil =>
            JsSuccess(EmptySeqParam)
          case _ =>
            JsError("Expected json string|number[int or float]|bool for multiple cortex pipeline parameter")
        }
      case _ => JsError(
        "Expected json string|number[int or float]|bool " +
          s"or sequence of one of these types for cortex pipeline parameter. Found [$json]"
      )
    }

    private def multipleParamsReads[T, A <: PipelineParam](
      values: List[JsValue],
      apply: List[T] => A
    )(implicit reads: Reads[T]): JsResult[A] = {
      values.foldM[JsResult, List[T]](List.empty) { case (soFar, jsValue) =>
        reads.reads(jsValue).map(_ :: soFar)
      }.map(list => apply(list.reverse))
    }

    override def writes(o: PipelineParam): JsValue = o match {
      case StringParam(value) => JsString(value)
      case IntParam(value) => JsNumber(value)
      case FloatParam(value) => CommonFormats.FloatWrites.writes(value)
      case BooleanParam(value) => JsBoolean(value)
      case StringParams(values) => JsArray(values.map(JsString))
      case IntParams(values) => JsArray(values.map(value => JsNumber(value)))
      case FloatParams(values) => JsArray(values.map(value => CommonFormats.FloatWrites.writes(value)))
      case BooleanParams(values) => JsArray(values.map(JsBoolean))
      case EmptySeqParam => JsArray.empty
    }
  }

  implicit val PipelineParamsFormat: Format[PipelineParams] = {
    val reads: Reads[PipelineParams] =
      for {
        map <- Reads.of[Map[String, JsValue]]
        pipelineReads = Reads.of[PipelineParam]
        result <- Reads(_ => map.toList.foldM[JsResult, Map[String, PipelineParam]](Map.empty) {
          case (soFar, (name, jsValue)) =>
            if (jsValue == JsNull) {
              JsSuccess(soFar)
            } else {
              pipelineReads.reads(jsValue).map { param => soFar.updated(name, param) }
            }
        })
      } yield PipelineParams(result)

    val writes: Writes[PipelineParams] = Writes(params => Json.toJson(params.value))
    Format(reads, writes)
  }

  def fromDomain(params: DomainPipelineParams): PipelineParams =
    PipelineParams(params.mapValues {
      case DomainStringParam(value) => StringParam(value)
      case DomainIntParam(value) => IntParam(value)
      case DomainFloatParam(value) => FloatParam(value)
      case DomainBooleanParam(value) => BooleanParam(value)
      case DomainStringParams(values) => StringParams(values)
      case DomainIntParams(values) => IntParams(values)
      case DomainFloatParams(values) => FloatParams(values)
      case DomainBooleanParams(values) => BooleanParams(values)
      case DomainEmptySeqParam => EmptySeqParam
    })
}
