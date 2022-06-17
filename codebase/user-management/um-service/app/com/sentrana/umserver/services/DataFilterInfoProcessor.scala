package com.sentrana.umserver.services

import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.entities.UserEntity
import com.sentrana.umserver.exceptions.ItemNotFoundException
import com.sentrana.umserver.shared.dtos.enums.FilterOperator
import com.sentrana.umserver.shared.dtos.{ DataFilterInstance, UserGroup }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Alexander on 31.05.2016.
 */
@Singleton
class DataFilterInfoProcessor @Inject() (
    userGroupQueryService:      UserGroupQueryService,
    organizationQueryService:   OrganizationQueryService,
    dataFilterInfoQueryService: DataFilterInfoQueryService
) {

  def getPreprocessedUserDataFilterInstances(user: UserEntity): Future[Map[String, DataFilterInstance]] = {
    val userFilterInstancesF = getUserFilterInstances(user)
    val userGroupFilterInstancesF = getUserGroupFilterInstances(user)
    val organizationFilterInstancesF = getOrganizationFilterInstances(user)

    for {
      userFilterInstances <- userFilterInstancesF
      userGroupFilterInstances <- userGroupFilterInstancesF
      organizationFilterInstances <- organizationFilterInstancesF
    } yield preprocessDataFilterInstances(userFilterInstances, userGroupFilterInstances, organizationFilterInstances)
  }

  private def getUserFilterInstances(user: UserEntity): Future[Map[String, Set[DataFilterInstance]]] = {
    val dataFilterInstancesF = getDataFilterInstancesF(user.dataFilterInstances)
    groupByFieldName(dataFilterInstancesF)
  }

  private def getUserGroupFilterInstances(user: UserEntity): Future[Map[String, Set[DataFilterInstance]]] = {
    val dataFilterInstancesF = getUserGroupsWithParents(user).flatMap { userGroups =>
      val dataFilterInstancesSeqF = userGroups.flatMap { userGroup =>
        getUserGroupDataFilterInstances(userGroup)
      }
      Future.sequence(dataFilterInstancesSeqF)
    }
    groupByFieldName(dataFilterInstancesF)
  }

  private def getOrganizationFilterInstances(user: UserEntity): Future[Map[String, Set[DataFilterInstance]]] = {
    val dataFilterInstancesF = organizationQueryService.getMandatory(user.organizationId).flatMap { organization =>
      getDataFilterInstancesF(organization.dataFilterInstances)
    }
    groupByFieldName(dataFilterInstancesF)
  }

  private def getDataFilterInstancesF(dataFilterInstances: Set[DataFilterInstance]): Future[Set[(String, DataFilterInstance)]] = {
    val dataFilterInstancesSeqF = dataFilterInstances.map { dataFilterInstance =>
      val dataFilterInfoF = dataFilterInfoQueryService.getMandatory(dataFilterInstance.dataFilterId)
      dataFilterInfoF.map(_.fieldName -> dataFilterInstance)
    }

    Future.sequence(dataFilterInstancesSeqF)
  }

  private def groupByFieldName(dataFilterInstancesF: Future[Set[(String, DataFilterInstance)]]): Future[Map[String, Set[DataFilterInstance]]] = {
    dataFilterInstancesF.map { dataFilterInstances =>
      dataFilterInstances.groupBy(_._1).map {
        case (fieldName, fieldNameDataFilterInstances) =>
          fieldName -> fieldNameDataFilterInstances.map(_._2)
      }
    }
  }

  private def getUserGroupDataFilterInstances(userGroup: UserGroup): Set[Future[(String, DataFilterInstance)]] = {
    val dataFilterInstancesSeqF = userGroup.dataFilterInstances.map { dataFilterInstance =>
      dataFilterInfoQueryService.getMandatory(dataFilterInstance.dataFilterId).map { dataFilterInfo =>
        dataFilterInfo.fieldName -> dataFilterInstance
      }
    }
    dataFilterInstancesSeqF
  }

  private[umserver] def preprocessDataFilterInstances(
    userFilterInstances:         Map[String, Set[DataFilterInstance]],
    userGroupFilterInstances:    Map[String, Set[DataFilterInstance]],
    organizationFilterInstances: Map[String, Set[DataFilterInstance]]
  ): Map[String, DataFilterInstance] = {

    val userElements = getDataFilterElementValues(userFilterInstances)
    val userGroupElements = getDataFilterElementValues(userGroupFilterInstances)
    val organizationElements = getDataFilterElementValues(organizationFilterInstances)

    val fieldNames = userElements.keySet ++ userGroupElements.keySet ++ organizationElements.keySet

    val res = fieldNames.flatMap { fieldName =>
      val dataFilterInfoId = getDataFilterInfoId(
        fieldName,
        userFilterInstances.get(fieldName),
        userGroupFilterInstances.get(fieldName),
        organizationFilterInstances.get(fieldName)
      )

      processDataFilterElements(fieldName, dataFilterInfoId,
        getDataFilterElements(fieldName, userElements),
        getDataFilterElements(fieldName, userGroupElements),
        getDataFilterElements(fieldName, organizationElements))
    }
    res.toMap
  }

  private def getDataFilterInfoId(
    fieldName:                   String,
    userFilterInstances:         Option[Set[DataFilterInstance]],
    userGroupFilterInstances:    Option[Set[DataFilterInstance]],
    organizationFilterInstances: Option[Set[DataFilterInstance]]
  ): String = {
    val filterInstances = userFilterInstances.getOrElse(userGroupFilterInstances.getOrElse(organizationFilterInstances.getOrElse(Set.empty)))
    filterInstances.headOption.map(_.dataFilterId).getOrElse(throw new ItemNotFoundException(s"DataFilterInstance with fieldName=$fieldName not found"))
  }

  private def getDataFilterElements(fieldName: String, dataFilterElements: Map[String, DataFilterElementValues]): DataFilterElementValues = {
    dataFilterElements.get(fieldName).getOrElse(emptyElements)
  }

  private def getDataFilterElementValues(dataFilterInstances: Map[String, Set[DataFilterInstance]]): Map[String, DataFilterElementValues] = {
    dataFilterInstances.map {
      case (fieldName, dataFieldInstances) =>
        fieldName -> prepareFilterElementsValues(dataFieldInstances)
    }
  }

  private def processDataFilterElements(
    fieldName:         String,
    fieldId:           String,
    userElements:      DataFilterElementValues,
    userGroupElements: DataFilterElementValues,
    orgElements:       DataFilterElementValues
  ): Option[(String, DataFilterInstance)] = {
    val intermediateNotAllowedValues = (userGroupElements.notAllowed -- userElements.allowed) ++ userElements.notAllowed
    val intermediateAllowedValues = (userGroupElements.allowed ++ userElements.allowed) -- intermediateNotAllowedValues

    val notAllowedValues = (orgElements.notAllowed -- intermediateAllowedValues) ++ intermediateNotAllowedValues
    val allowedValues = (orgElements.allowed ++ intermediateAllowedValues) -- notAllowedValues

    getDataFilterInstance(fieldName, fieldId, allowedValues, notAllowedValues)
  }

  private def getDataFilterInstance(
    fieldName:        String,
    filterId:         String,
    allowedValues:    Set[String],
    notAllowedValues: Set[String]
  ): Option[(String, DataFilterInstance)] = {
    if (allowedValues.size > 0) {
      Option(fieldName -> DataFilterInstance(filterId, FilterOperator.IN, allowedValues))
    }
    else if (notAllowedValues.size > 0) {
      Option(fieldName -> DataFilterInstance(filterId, FilterOperator.NOT_IN, notAllowedValues))
    }
    else {
      None
    }
  }

  private def getUserGroupsWithParents(user: UserEntity): Future[Set[UserGroup]] = {
    val userGroupsSet = user.groupIds.map { userGroupQueryService.getUserGroupHierarchy(user.organizationId, _) }
    Future.sequence(userGroupsSet).map(_.flatten)
  }

  private[umserver] def prepareFilterElementsValues(dataFilterInstances: Set[DataFilterInstance]): DataFilterElementValues = {
    val inDataFilterValues = filterDataFilterInstances(dataFilterInstances, FilterOperator.IN)
    val notInDataFilterValues = filterDataFilterInstances(dataFilterInstances, FilterOperator.NOT_IN)
    val eqDataFilterValues = filterDataFilterInstances(dataFilterInstances, FilterOperator.EQ)
    val notEqDataFilterValues = filterDataFilterInstances(dataFilterInstances, FilterOperator.NOT_EQ)

    val allowed = inDataFilterValues ++ eqDataFilterValues -- notEqDataFilterValues -- notInDataFilterValues
    val notAllowed = notEqDataFilterValues ++ notInDataFilterValues
    DataFilterElementValues(allowed, notAllowed)
  }

  private def filterDataFilterInstances(dataFilterInstances: Set[DataFilterInstance], filterOperator: FilterOperator): Set[String] = {
    dataFilterInstances.collect {
      case dataFilterInstance if (dataFilterInstance.operator == filterOperator) => dataFilterInstance.values
    }.flatten
  }

  private val emptyElements = DataFilterElementValues(Set.empty, Set.empty)
  private val dummyId = "dummyId"
}

case class DataFilterElementValues(
  allowed:    Set[String],
  notAllowed: Set[String]
)
