package com.sentrana.umserver.services

import com.sentrana.umserver.entities.UserEntity
import com.sentrana.umserver.shared.dtos.{DataFilterInstance, Organization, UserGroup}
import com.sentrana.umserver.shared.dtos.enums.FilterOperator
import com.sentrana.umserver.{IntegrationTestUtils, OneAppWithMongo}
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
/**
  * Created by Alexander on 31.05.2016.
  */
class DataFilterInfoProcessorSpec extends PlaySpec with OneAppWithMongo {
  private lazy val dataFilterInfoProcessor = app.injector.instanceOf(classOf[DataFilterInfoProcessor])
  private implicit lazy val userGroupService = app.injector.instanceOf(classOf[UserGroupService])
  private implicit lazy val organizationService = app.injector.instanceOf(classOf[OrganizationService])

  private lazy val itUtils = new IntegrationTestUtils()
  private val IT_PREFIX = "integrationTest_"
  private val USER1_PASSWORD = "myPassword"

  private val dataFilterId = "testDataFilterId"
  private val testDataFilterInstance = DataFilterInstance(
    dataFilterId,
    FilterOperator.EQ,
    values = Set()
  )

  private val USER_DATA_FILTER_INSTANCE_VALUE_IN = "USER_DATA_FILTER_INSTANCE_VALUE_IN"
  private val USER_DATA_FILTER_INSTANCE_VALUE_EQ = "USER_DATA_FILTER_INSTANCE_VALUE_EQ"
  private val USER_DATA_FILTER_INSTANCE_VALUE_NOT_IN = "USER_DATA_FILTER_INSTANCE_VALUE_NOT_IN"
  private val USER_DATA_FILTER_INSTANCE_VALUE_NOT_EQ = "USER_DATA_FILTER_INSTANCE_VALUE_NOT_EQ"

  private lazy val userDataFilterInstances = Set[DataFilterInstance](
    testDataFilterInstance.copy(dataFilterInfo.id, FilterOperator.IN, Set(USER_DATA_FILTER_INSTANCE_VALUE_IN)),
    testDataFilterInstance.copy(dataFilterInfo2.id, FilterOperator.IN, Set(USER_DATA_FILTER_INSTANCE_VALUE_IN)),
    testDataFilterInstance.copy(dataFilterInfo.id, FilterOperator.EQ, Set(USER_DATA_FILTER_INSTANCE_VALUE_EQ)),
    testDataFilterInstance.copy(dataFilterInfo2.id, FilterOperator.NOT_IN, Set(USER_DATA_FILTER_INSTANCE_VALUE_NOT_IN)),
    testDataFilterInstance.copy(dataFilterInfo2.id, FilterOperator.NOT_EQ, Set(USER_DATA_FILTER_INSTANCE_VALUE_NOT_EQ))
  )

  private val ORG_DATA_FILTER_INSTANCE_VALUE_NOT_IN = "ORG_DATA_FILTER_INSTANCE_VALUE_NOT_IN"
  private val ORG_DATA_FILTER_INSTANCE_VALUE_IN = "ORG_DATA_FILTER_INSTANCE_VALUE_IN"
  private val USER_GROUP_FILTER_INSTANCE_VALUE_EQ = "USER_GROUP_FILTER_INSTANCE_VALUE_EQ"
  private val USER_GROUP_FILTER_INSTANCE_VALUE_NOT_EQ = "USER_GROUP_FILTER_INSTANCE_VALUE_NOT_EQ"

  private val FOO = "FOO"
  private val BAR = "bar"
  private val userDataFilter = "userDataFilter"

  private lazy val orgDataFilterInstances = Set[DataFilterInstance](
    testDataFilterInstance.copy(dataFilterInfo.id, FilterOperator.NOT_IN, Set(USER_DATA_FILTER_INSTANCE_VALUE_IN)),
    testDataFilterInstance.copy(dataFilterInfo.id, FilterOperator.NOT_EQ, Set(USER_DATA_FILTER_INSTANCE_VALUE_EQ)),
    testDataFilterInstance.copy(dataFilterInfo2.id, FilterOperator.NOT_IN, Set(ORG_DATA_FILTER_INSTANCE_VALUE_NOT_IN)),
    testDataFilterInstance.copy(dataFilterInfo2.id, FilterOperator.IN, Set(ORG_DATA_FILTER_INSTANCE_VALUE_IN))
  )

  private lazy val userGroupDataFilterInstances = Set[DataFilterInstance](
    testDataFilterInstance.copy(dataFilterInfo.id, FilterOperator.EQ, Set(USER_GROUP_FILTER_INSTANCE_VALUE_EQ)),
    testDataFilterInstance.copy(dataFilterInfo.id, FilterOperator.NOT_EQ, Set(USER_GROUP_FILTER_INSTANCE_VALUE_NOT_EQ)),
    testDataFilterInstance.copy(dataFilterInfo2.id, FilterOperator.NOT_IN, Set(USER_DATA_FILTER_INSTANCE_VALUE_IN)),
    testDataFilterInstance.copy(dataFilterInfo.id, FilterOperator.NOT_EQ, Set(USER_DATA_FILTER_INSTANCE_VALUE_EQ))
  )

  private val dataFilterInfoFieldId = "userDataFilter"

  private lazy val rootOrg = itUtils.createRootOrg()
  private lazy val org1 = itUtils.createTestOrg("org1", rootOrg.id, dataFilterInstance = orgDataFilterInstances)
  private lazy val group1 = itUtils.createTestGroup(IT_PREFIX + "sampleUserGroup1", None, orgId = org1.id, dataFilterInstances = userGroupDataFilterInstances)
  private lazy val user1 = itUtils.createTestUser(IT_PREFIX + "sampleUser1",
    USER1_PASSWORD,
    orgId = org1.id,
    groupIds = Set(group1.id),
    dataFilterInstances = userDataFilterInstances)
  private lazy val dataFilterInfo = itUtils.createTestDataFilterInfo(dataFilterInfoFieldId)
  private lazy val dataFilterInfo2 = itUtils.createTestDataFilterInfo(dataFilterInfoFieldId)

  private val aaa = "aaa"
  private val ddd = "ddd"
  private val www = "www"
  private val qqq = "qqq"
  private val eee = "eee"
  private val sss = "sss"

  val fieldName = "testFieldName"

  "DataFilterInfoProcessor" must {
    "NOT_IN has higher priority than IN" in {
      val dataFilterInstances = Set(testDataFilterInstance.copy(operator = FilterOperator.NOT_IN, values = Set(FOO)),
        testDataFilterInstance.copy(operator = FilterOperator.IN, values = Set(FOO, BAR))
      )
      val dataFilterElementValues = dataFilterInfoProcessor.prepareFilterElementsValues(dataFilterInstances)
      dataFilterElementValues.allowed must contain only(BAR)
      dataFilterElementValues.notAllowed must contain only(FOO)
    }

    "NOT_EQ has higher priority than EQ" in {
      val dataFilterInstances = Set(testDataFilterInstance.copy(operator = FilterOperator.NOT_EQ, values = Set(FOO)),
        testDataFilterInstance.copy(operator = FilterOperator.EQ, values = Set(FOO, BAR))
      )
      val dataFilterElementValues = dataFilterInfoProcessor.prepareFilterElementsValues(dataFilterInstances)
      dataFilterElementValues.allowed must contain only(BAR)
      dataFilterElementValues.notAllowed must contain only(FOO)
    }

    "should return IN + EQ - NOT_EQ - NOT_IN" in {
      val value1 = "Value1"
      val value2 = "Value2"
      val value3 = "Value3"
      val value4 = "Value4"
      val value5 = "Value5"
      val value6 = "Value6"
      val dataFilterInstances = Set(testDataFilterInstance.copy(operator = FilterOperator.NOT_IN, values = Set(value1, value2)),
        testDataFilterInstance.copy(operator = FilterOperator.IN, values = Set(value1, value3)),
        testDataFilterInstance.copy(operator = FilterOperator.EQ, values = Set(value4, value5)),
        testDataFilterInstance.copy(operator = FilterOperator.NOT_EQ, values = Set(value4, value6))
      )

      val dataFilterElementValues = dataFilterInfoProcessor.prepareFilterElementsValues(dataFilterInstances)
      dataFilterElementValues.allowed must contain only(value3, value5)
      dataFilterElementValues.notAllowed must contain only(value1, value2, value4, value6)
    }

    "org filter elements have lower priority than userGroup filter elements" in {
      val res = dataFilterInfoProcessor.preprocessDataFilterInstances(
        Map(),
        Map(fieldName -> Set(IN(aaa, ddd), NOT_IN(qqq, eee))),
        Map(fieldName -> Set(IN(qqq, www), NOT_IN(aaa, sss)))
      )
      res(fieldName).operator mustBe FilterOperator.IN
      res(fieldName).values must contain only (aaa, ddd, www)
    }

    "org filter elements have lower priority than user filter elements" in {
      val res = dataFilterInfoProcessor.preprocessDataFilterInstances(
        Map(fieldName -> Set(IN(aaa, ddd), NOT_IN(qqq, eee))),
        Map(),
        Map(fieldName -> Set(IN(qqq, www), NOT_IN(aaa, sss)))
      )
      res(fieldName).operator mustBe FilterOperator.IN
      res(fieldName).values must contain only (aaa, ddd, www)
    }

    "userGroup filter elements have lower priority than user filter elements" in {
      val res = dataFilterInfoProcessor.preprocessDataFilterInstances(
        Map(fieldName -> Set(IN(aaa, ddd), NOT_IN(qqq, eee))),
        Map(fieldName -> Set(IN(qqq, www), NOT_IN(aaa, sss))),
        Map()
      )
      res(fieldName).operator mustBe FilterOperator.IN
      res(fieldName).values must contain only (aaa, ddd, www)
    }

    "negative filters have higher priority on the same lvl" in {
      val res = dataFilterInfoProcessor.preprocessDataFilterInstances(
        Map(fieldName -> Set(IN(aaa, ddd), NOT_IN(qqq, eee))),
        Map(fieldName -> Set(IN(qqq, www), NOT_IN(www), NOT_IN(aaa, sss))),
        Map()
      )
      res(fieldName).operator mustBe FilterOperator.IN
      res(fieldName).values must contain only (aaa, ddd)
    }

    "2 filters refers to a single field on the same level" in {
      val anotherDataFilterInfo = itUtils.createTestDataFilterInfo(dataFilterInfoFieldId)
      val anotherIn = NOT_IN(www).copy(dataFilterId = anotherDataFilterInfo.id)

      val res = dataFilterInfoProcessor.preprocessDataFilterInstances(
        Map(fieldName -> Set(IN(aaa, ddd), NOT_IN(qqq, eee))),
        Map(fieldName -> Set(IN(qqq, www), anotherIn, NOT_IN(aaa, sss))),
        Map()
      )
      res(fieldName).operator mustBe FilterOperator.IN
      res(fieldName).values must contain only (aaa, ddd)
    }

    "2 filters refers to a single field on the different levels" in {
      val anotherDataFilterInfo = itUtils.createTestDataFilterInfo(dataFilterInfoFieldId)
      val anotherIn = NOT_IN(www).copy(dataFilterId = anotherDataFilterInfo.id)

      val res = dataFilterInfoProcessor.preprocessDataFilterInstances(
        Map(fieldName -> Set(IN(aaa, ddd), anotherIn, NOT_IN(qqq, eee))),
        Map(fieldName -> Set(IN(qqq, www), NOT_IN(aaa, sss))),
        Map()
      )
      res(fieldName).operator mustBe FilterOperator.IN
      res(fieldName).values must contain only (aaa, ddd)
    }

    "2 filters refers to a single field on the different levels, but with lower priority" in {
      val anotherDataFilterInfo = itUtils.createTestDataFilterInfo(dataFilterInfoFieldId)
      val anotherIn = NOT_IN(www).copy(dataFilterId = anotherDataFilterInfo.id)

      val res = dataFilterInfoProcessor.preprocessDataFilterInstances(
        Map(fieldName -> Set(IN(aaa, ddd), NOT_IN(qqq, eee))),
        Map(fieldName -> Set(IN(qqq, www), NOT_IN(aaa, sss))),
        Map(fieldName -> Set[DataFilterInstance](anotherIn))
      )
      res(fieldName).operator mustBe FilterOperator.IN
      res(fieldName).values must contain only (aaa, ddd, www)
    }

    "negative filter operator in result dataFilterInstance" in {
      val res = dataFilterInfoProcessor.preprocessDataFilterInstances(
        Map(fieldName -> Set(IN(qqq), NOT_IN(qqq, eee))),
        Map(fieldName -> Set(IN(aaa, www), NOT_IN(aaa, sss, www))),
        Map(fieldName -> Set(IN(aaa, ddd), NOT_IN(ddd)))
      )
      res(fieldName).operator mustBe FilterOperator.NOT_IN
      res(fieldName).values must contain only (aaa, ddd, qqq, www, eee, sss)
    }

    "return Map of fieldNames dataFilterInstances" in {
      val preprocessedDataFilterInstances = await(dataFilterInfoProcessor.getPreprocessedUserDataFilterInstances(user1))
      preprocessedDataFilterInstances.size mustBe 1
      preprocessedDataFilterInstances.get(userDataFilter) mustBe defined
    }
  }

  private def IN(elems: String*): DataFilterInstance = {
    testDataFilterInstance.copy(dataFilterInfo.id, FilterOperator.IN, elems.toSet)
  }

  private def NOT_IN(elems: String*): DataFilterInstance = {
    testDataFilterInstance.copy(dataFilterInfo.id, FilterOperator.NOT_IN, elems.toSet)
  }

  private def createOrg(name: String, dataFilterInstances:  Set[DataFilterInstance]): Organization = {
    itUtils.createTestOrg(name, rootOrg.id, dataFilterInstance = dataFilterInstances)
  }

  private def createUserGroup(name: String, orgId: String, dataFilterInstances:  Set[DataFilterInstance]): UserGroup = {
    itUtils.createTestGroup(IT_PREFIX + name, None, orgId = orgId, dataFilterInstances = dataFilterInstances, forChildOrgs = true)
  }

  private def createUser(name: String, orgId: String, groupId: String, dataFilterInstances:  Set[DataFilterInstance]): UserEntity = {
    itUtils.createTestUser(IT_PREFIX + name, USER1_PASSWORD, groupIds = Set(groupId), orgId = orgId, dataFilterInstances = dataFilterInstances)
  }
}
