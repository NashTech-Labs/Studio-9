package com.sentrana.umserver.dtos

import com.sentrana.umserver.shared.dtos.DataFilterInfoSettings
import com.sentrana.umserver.shared.dtos.enums.DBType
import play.api.libs.json.Json

/**
 * Created by Alexander on 26.05.2016.
 */
case class CreateDataFilterInfoRequest(
  fieldName:           String,
  fieldDesc:           String,
  valuesQuerySettings: Option[DataFilterInfoSettings],
  displayName:         Option[String],
  showValueOnly:       Boolean
)

object CreateDataFilterInfoRequest {
  import com.sentrana.umserver.JsonFormats._

  implicit val reads = Json.reads[CreateDataFilterInfoRequest]
}

case class UpdateDataFilterInfoSettingsRequest(
  validValuesQuery: Option[String],
  dbName:           Option[String],
  dbType:           Option[DBType],
  dataType:         Option[String],
  collectionName:   Option[String]
)

object UpdateDataFilterInfoSettingsRequest {
  import com.sentrana.umserver.JsonFormats._

  implicit val updateDataFilterInfoSettingsRequestFormat = Json.format[UpdateDataFilterInfoSettingsRequest]
}

case class UpdateDataFilterInfoRequest(
  fieldName:                Option[String],
  fieldDesc:                Option[String],
  resetValuesQuerySettings: Option[Boolean],
  valuesQuerySettings:      Option[UpdateDataFilterInfoSettingsRequest],
  displayName:              Option[String],
  showValueOnly:            Option[Boolean]
)

object UpdateDataFilterInfoRequest {
  import UpdateDataFilterInfoSettingsRequest._
  import com.sentrana.umserver.JsonFormats._

  implicit val reads = Json.reads[UpdateDataFilterInfoRequest]
}
