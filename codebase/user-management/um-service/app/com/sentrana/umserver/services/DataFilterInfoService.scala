package com.sentrana.umserver.services

import java.time.Clock
import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.dtos.{ CreateDataFilterInfoRequest, UpdateDataFilterInfoRequest }
import com.sentrana.umserver.entities.MongoFormats
import com.sentrana.umserver.shared.dtos.{ DataFilterInfo, DataFilterInfoSettings }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Alexander on 25.05.2016.
 */
@Singleton
class DataFilterInfoService @Inject() (
  clock:                       Clock,
  qService:                    DataFilterInfoQueryService,
  implicit val mongoDbService: MongoDbService
)
    extends EntityCommandService[CreateDataFilterInfoRequest, UpdateDataFilterInfoRequest, DataFilterInfo] {

  override protected implicit val mongoEntityFormat = MongoFormats.dataFilterInfoMongoFormat

  override protected def EntityName: String = "DataFilterInfo"

  override def create(req: CreateDataFilterInfoRequest): Future[DataFilterInfo] = {
    val entity = DataFilterInfo(
      id                  = mongoDbService.generateId,
      fieldName           = req.fieldName,
      fieldDesc           = req.fieldDesc,
      valuesQuerySettings = req.valuesQuerySettings,
      displayName         = req.displayName,
      showValueOnly       = req.showValueOnly
    )
    mongoDbService.save(entity)
  }

  override def update(dataFilterInfoId: String, req: UpdateDataFilterInfoRequest): Future[DataFilterInfo] = {
    qService.getMandatory(dataFilterInfoId) flatMap { dataFilterInfo =>

      val valuesQuerySettings = for {
        updateSettings <- req.valuesQuerySettings
        currentSettings = currentDataFilterInfoSettings(dataFilterInfo)
      } yield DataFilterInfoSettings(
        validValuesQuery = updateSettings.validValuesQuery.getOrElse(currentSettings.validValuesQuery),
        dbName           = updateSettings.dbName.getOrElse(currentSettings.dbName),
        dbType           = updateSettings.dbType.getOrElse(currentSettings.dbType),
        dataType         = updateSettings.dataType.getOrElse(currentSettings.dataType),
        collectionName   = updateSettings.collectionName.orElse(currentSettings.collectionName)
      )

      val dataFilterInfoUpd = dataFilterInfo.copy(
        fieldName           = req.fieldName.getOrElse(dataFilterInfo.fieldName),
        fieldDesc           = req.fieldDesc.getOrElse(dataFilterInfo.fieldDesc),
        valuesQuerySettings = valuesQuerySettings.orElse(dataFilterInfo.valuesQuerySettings).filterNot(_ => req.resetValuesQuerySettings == Some(true)),
        displayName         = req.displayName.orElse(dataFilterInfo.displayName),
        showValueOnly       = req.showValueOnly.getOrElse(dataFilterInfo.showValueOnly)
      )
      mongoDbService.update(dataFilterInfoUpd, OrgScopeRoot)
    }
  }

  private def currentDataFilterInfoSettings(dataFilterInfo: DataFilterInfo): DataFilterInfoSettings = {
    dataFilterInfo.valuesQuerySettings.getOrElse(
      DataFilterInfoSettings(
        validValuesQuery = "",
        dbName           = "",
        dataType         = ""
      )
    )
  }

  override def delete(dataFilterInfoId: String): Future[_] = {
    mongoDbService.delete[DataFilterInfo](dataFilterInfoId, OrgScopeRoot)
  }
}
