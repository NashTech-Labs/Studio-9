package argo.domain.rest.config

import java.util.Date

import argo.domain.rest.HttpContract

case class CreateUpdateConfigSettingContract(
    settingValue: String,
    tags:         List[String]
) extends HttpContract {
  require(settingValue.nonEmpty, "settingValue must not be empty")
}

case class ConfigSettingContract(
  serviceName:  String,
  settingName:  String,
  settingValue: String,
  tags:         List[String],
  createdAt:    Date,
  updatedAt:    Date
) extends HttpContract
