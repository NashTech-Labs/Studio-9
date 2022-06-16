package argo.domain.service.config

import java.util.{ Date, UUID }

import argo.domain.service.{ DomainObject, UUIDEntity }

case class ConfigSetting(
  id:            UUID,
  service_name:  String,
  setting_name:  String,
  setting_value: String,
  tags:          List[String],
  created_at:    Date,
  updated_at:    Date
) extends UUIDEntity

case class ConfigSettingSearchCriteria(
  owner: Option[UUID] = None
) extends DomainObject
