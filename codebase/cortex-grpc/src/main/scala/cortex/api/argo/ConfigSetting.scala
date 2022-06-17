package cortex.api.argo

import java.util.Date

import play.api.libs.json.{ Json, OFormat }

/** Configuration setting.
  * @param serviceName Name of the service which this setting is for.
  */
case class ConfigSetting(
    serviceName: String,
    settingName: String,
    settingValue: String,
    tags: Seq[String],
    createdAt: Date,
    updatedAt: Date
)

object ConfigSetting {
  implicit val format: OFormat[ConfigSetting] = Json.format[ConfigSetting]
}
