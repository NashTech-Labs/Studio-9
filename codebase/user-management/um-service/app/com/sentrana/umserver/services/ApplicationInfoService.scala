package com.sentrana.umserver.services

import java.time.{ Clock, ZonedDateTime }
import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.dtos.{ CreateApplicationInfoRequest, UpdateApplicationInfoRequest }
import com.sentrana.umserver.entities.{ ApplicationInfoEntity, MongoFormats }
import com.sentrana.umserver.exceptions.ValidationException
import org.apache.commons.lang3.RandomStringUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Alexander on 28.04.2016.
 */
@Singleton
class ApplicationInfoService @Inject() (
  clock:                       Clock,
  qService:                    ApplicationInfoQueryService,
  implicit val mongoDbService: MongoDbService
)
    extends EntityCommandService[CreateApplicationInfoRequest, UpdateApplicationInfoRequest, ApplicationInfoEntity] {

  override protected implicit val mongoEntityFormat = MongoFormats.applicationInfoEntityFormat

  override protected def EntityName: String = "ApplicationInfo"

  private def validateApplicationInfoName(applicationInfoId: String, applicationInfoName: String): Future[String] = {
    val params = Map("name" -> Seq(applicationInfoName), "id_not" -> Seq(applicationInfoId))
    qService.find(params).map { appInfos =>
      if (!appInfos.isEmpty) throw new ValidationException(s"Non unique applicationInfo's name ${applicationInfoName}") else applicationInfoName
    }
  }

  override def create(req: CreateApplicationInfoRequest): Future[ApplicationInfoEntity] = {
    val now = ZonedDateTime.now(clock)
    val id = mongoDbService.generateId

    validateApplicationInfoName(id, req.name).flatMap { appInfoName =>
      val entity = ApplicationInfoEntity(
        id                   = appInfoName,
        name                 = req.name,
        desc                 = req.desc,
        url                  = req.url,
        passwordResetUrl     = req.passwordResetUrl,
        emailConfirmationUrl = req.emailConfirmationUrl,
        clientSecret         = generateClientSecret(),
        created              = now,
        updated              = now
      )
      mongoDbService.save(entity)
    }
  }

  override def update(applicationInfoId: String, req: UpdateApplicationInfoRequest): Future[ApplicationInfoEntity] = {
    qService.getMandatory(applicationInfoId) flatMap { appInfo =>

      val appInfoNameF = req.name.map { appInfoName =>
        validateApplicationInfoName(applicationInfoId, appInfoName)
      }.fold(Future.successful(appInfo.name))(appInfoName => appInfoName)

      appInfoNameF.flatMap { appInfoName =>
        val appInfoUpd = appInfo.copy(
          name                 = appInfoName,
          desc                 = req.desc.orElse(appInfo.desc),
          url                  = req.url.orElse(appInfo.url),
          passwordResetUrl     = req.passwordResetUrl.orElse(appInfo.url),
          emailConfirmationUrl = req.emailConfirmationUrl.orElse(appInfo.emailConfirmationUrl),
          updated              = ZonedDateTime.now(clock)
        )
        mongoDbService.update(appInfoUpd, OrgScopeRoot)
      }
    }
  }

  override def delete(id: String): Future[_] = mongoDbService.delete[ApplicationInfoEntity](id, OrgScopeRoot)

  def regenerateClientSecret(id: String): Future[ApplicationInfoEntity] = {
    qService.getMandatory(id) flatMap { appInfo =>
      val appInfoUpd = appInfo.copy(clientSecret = generateClientSecret())
      mongoDbService.update(appInfoUpd, OrgScopeRoot)
    }
  }

  def byName(name: String): Future[Option[ApplicationInfoEntity]] = {
    import org.mongodb.scala.model.Filters._
    mongoDbService.findSingle[ApplicationInfoEntity](equal("name", name))
  }

  private def generateClientSecret() = RandomStringUtils.randomAlphanumeric(20)
}
