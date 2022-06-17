package cortex.api.argo

import play.api.libs.json.{ Json, OFormat }

/**
  * Used for setting or updating configuration value.
  */
case class UpdateConfigSetting(settingValue: String, tags: Seq[String])

object UpdateConfigSetting {
  implicit val format: OFormat[UpdateConfigSetting] = Json.format[UpdateConfigSetting]
}
