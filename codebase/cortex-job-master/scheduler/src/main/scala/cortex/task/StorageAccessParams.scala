package cortex.task

import play.api.libs.json._
import cortex.JsonSupport.SnakeJson

sealed trait StorageAccessParams {
  val storageType: String
}

object StorageAccessParams {
  case class S3AccessParams(
      bucket:       String,
      accessKey:    String,
      secretKey:    String,
      region:       String,
      sessionToken: Option[String] = None,
      endpointUrl:  Option[String] = None
  ) extends StorageAccessParams {
    override val storageType: String = "S3"
  }

  case object LocalAccessParams extends StorageAccessParams {
    override val storageType: String = "LOCAL"
  }

  implicit val s3AccessParamsReads: Reads[S3AccessParams] = SnakeJson.reads[S3AccessParams]

  implicit object StorageAccessParamsWrites extends Writes[StorageAccessParams] {
    private val s3AccessParamsWrites = SnakeJson.writes[S3AccessParams]

    override def writes(o: StorageAccessParams): JsValue = {
      (o match {
        case s3AccessParams: S3AccessParams => SnakeJson.toJsObject(s3AccessParams)(s3AccessParamsWrites)
        case LocalAccessParams              => JsObject.empty
      }) ++ JsObject(Seq("storage_type" -> JsString(o.storageType)))
    }
  }
}
