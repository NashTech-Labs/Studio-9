package cortex.task

import cortex.JsonSupport.SnakeJson
import cortex.task.TabularAccessParams.RedshiftAccessParams
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import play.api.libs.json.{ JsNumber, JsObject, JsString }

class TabularAccessParamsSerializationTest extends FlatSpec {

  "json serializer" should "serialize RedshiftAccessParams properly" in {
    //scalastyle:off magic.number
    val accessParams = RedshiftAccessParams(
      hostname  = "localhost",
      port      = 1234,
      username  = "user",
      password  = "pass",
      database  = "db",
      s3IamRole = "admin"
    )
    val expectedJson = JsObject(Map(
      "hostname" -> JsString("localhost"),
      "port" -> JsNumber(1234),
      "username" -> JsString("user"),
      "password" -> JsString("pass"),
      "database" -> JsString("db"),
      "s3_iam_role" -> JsString("admin"),
      "db_type" -> JsString("REDSHIFT")
    ))

    val serializedAccessParams = SnakeJson.toJson[RedshiftAccessParams](accessParams)

    serializedAccessParams shouldBe expectedJson
  }
}
