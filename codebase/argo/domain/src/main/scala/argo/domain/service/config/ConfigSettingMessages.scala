package argo.domain.service.config

import argo.domain.service.ServiceMessage

// Commands
case class CreateUpdateConfigSetting(serviceName: String, settingName: String, settingValue: String, tags: List[String]) extends ServiceMessage
case class DeleteConfigSetting(serviceName: String, settingName: String) extends ServiceMessage

// Queries
case class RetrieveConfigSetting(serviceName: String, settingName: String) extends ServiceMessage
case class RetrieveConfigSettings(serviceName: String) extends ServiceMessage
