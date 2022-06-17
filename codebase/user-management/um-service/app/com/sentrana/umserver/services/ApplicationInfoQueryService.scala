package com.sentrana.umserver.services

import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.entities.{ MongoFormats, MongoEntityFormat, ApplicationInfoEntity }
import com.sentrana.umserver.shared.dtos.ApplicationInfo

import scala.concurrent.Future

/**
 * Created by Paul Lysak on 17.06.16.
 */
@Singleton
class ApplicationInfoQueryService @Inject() (protected val mongoDbService: MongoDbService)
    extends EntityQueryService[ApplicationInfoEntity] {
  override protected implicit val mongoEntityFormat = MongoFormats.applicationInfoEntityFormat

  override protected def EntityName: String = "ApplicationInfo"

  override protected val filterFields: Set[String] = super.filterFields ++ Set("name")
}
