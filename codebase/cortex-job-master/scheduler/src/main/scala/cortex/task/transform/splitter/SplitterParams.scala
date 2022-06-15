package cortex.task.transform.splitter

import cortex.task.StorageAccessParams
import cortex.{ TaskParams, TaskResult }
import play.api.libs.json._
import cortex.JsonSupport.SnakeJson

object SplitterParams {
  case class SplitterTaskParams(
      inputPaths:          Seq[String],
      storageAccessParams: StorageAccessParams,
      splits:              Option[Int]         = None,
      outputPrefix:        Option[String]      = None,
      outputBasePath:      Option[String]      = None
  ) extends TaskParams

  case class SplitterTaskResult(taskId: String, parts: Seq[String]) extends TaskResult {
    override def toString: String = parts.mkString(", ")
  }

  implicit val splitterTaskParamsWrites: OWrites[SplitterTaskParams] = SnakeJson.writes[SplitterTaskParams]

  implicit val splitterTaskResultReads: Reads[SplitterTaskResult] = SnakeJson.reads[SplitterTaskResult]
}
