package com.sentrana.umserver

import java.time.ZonedDateTime

import com.sentrana.umserver.entities.UserEntity
import com.sentrana.umserver.services.{ OrganizationQueryService, UserGroupQueryService, UserConverter }
import com.sentrana.umserver.shared.dtos.{ Permission, Organization }
import com.sentrana.umserver.shared.dtos.enums.{ UserConverterOptions, UserStatus }
import org.scalatest.{ OptionValues, MustMatchers, WordSpec }
import org.mockito.Mockito._
import play.api.test.Helpers._

import scala.concurrent.Future

/**
 * Created by Paul Lysak on 28.06.16.
 */
class UserConverterSpec extends WordSpec with MustMatchers with OptionValues {
  "UserConverter" must {
    "convert to DTO" in {
      val uc = mockedUserConverter()
      val dto = uc.toUserDto(sampleUserEntity)
      dto.id must be (sampleUserEntity.id)
      dto.username must be (sampleUserEntity.username)
      dto.organization must be (empty)
      dto.permissions must be (empty)
    }

    "convert with permission details" in {
      val uc = mockedUserConverter()
      val dto = await(uc.toUserDetailDto(sampleUserEntity, Map.empty))
      dto.id must be (sampleUserEntity.id)
      dto.permissions must be (Set("DO_THAT"))
    }

    "load org details" in {
      val uc = mockedUserConverter()
      val dto = await(uc.toUserDetailDto(sampleUserEntity, Map(UserConverterOptions.withOrgDetails.toString -> Nil)))
      dto.id must be (sampleUserEntity.id)
      val org = dto.organization.value
      org.name must be (sampleOrg.name)
    }

    "convert user time zones" in {
      val uc = mockedUserConverter()
      val dto = await(uc.toUserDetailDto(sampleUserEntity, Map(UserConverterOptions.withTimeZone.toString -> Seq("UTC"))))
      dto.id must be (sampleUserEntity.id)
      dto.created.toString must startWith (sampleDateUtcStr)
      dto.updated.toString must startWith (sampleDateUtcStr)
    }

    "convert org time zones" in {
      val uc = mockedUserConverter()
      val dto = await(uc.toUserDetailDto(
        sampleUserEntity,
        Map(
          UserConverterOptions.withOrgDetails.toString -> Seq.empty,
          UserConverterOptions.withTimeZone.toString -> Seq("UTC")
        )
      ))
      dto.id must be (sampleUserEntity.id)
      val org = dto.organization.value
      org.created.toString must startWith (sampleDateUtcStr)
      org.updated.toString must startWith (sampleDateUtcStr)
    }

  }

  private def mockedUserConverter(): UserConverter = {
    val gqs = mock(classOf[UserGroupQueryService])
    when(gqs.getRecursivePermisions("org1", "g1")).thenReturn(Future.successful(Set(Permission("DO_THAT"))))
    val oqs = mock(classOf[OrganizationQueryService])
    when(oqs.getMandatory("org1")).thenReturn(Future.successful(sampleOrg))

    new UserConverter(gqs, oqs)
  }

  private val sampleDateGmt3Dst = ZonedDateTime.parse("2016-06-28T18:00+03:00")
  private val sampleDateUtcStr = "2016-06-28T15:00Z"

  private val sampleUserEntity = UserEntity(
    id                  = "uid1",
    username            = "un1",
    email               = "em1@q.w",
    password            = "pwd1",
    firstName           = "fn1",
    lastName            = "ln1",
    status              = UserStatus.ACTIVE,
    created             = sampleDateGmt3Dst,
    updated             = sampleDateGmt3Dst,
    dataFilterInstances = Set.empty,
    groupIds            = Set("g1"),
    organizationId      = "org1"
  )

  private val sampleOrg = Organization(
    id      = "org1",
    name    = "on1",
    created = sampleDateGmt3Dst,
    updated = sampleDateGmt3Dst
  )

}
