package com.sentrana.umserver.services

import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.entities.MongoFormats
import com.sentrana.umserver.exceptions.{ DataRetrievalException, ValidationException }
import com.sentrana.umserver.shared.dtos.{ DataFilterInfo, DataFilterInfoSettings }
import com.sentrana.umserver.shared.dtos.enums.DBType
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.Configuration
import play.api.libs.json.Json

import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Success }

/**
 * Created by Paul Lysak on 17.06.16.
 */
@Singleton
class DataFilterInfoQueryService @Inject() (
  val mongoDbService: MongoDbService,
  queryExecutor:      QueryExecutor,
  configuration:      Configuration
)
    extends EntityQueryService[DataFilterInfo] {
  import DataExtractor._

  private implicit val validValuesFormat = Json.format[ValidValues]

  def getValidValues(dataFilterInfoId: String): Future[ListMap[String, String]] = {
    getMandatory(dataFilterInfoId).flatMap { dataFilterInfo =>
      dataFilterInfo.valuesQuerySettings.map { valuesQuerySettings =>
        valuesQuerySettings.dbType match {
          case DBType.SQL   => getSqlValidValues(valuesQuerySettings)
          case DBType.MONGO => getMongoValidValues(valuesQuerySettings)
          case dbType       => throw new ValidationException(s"Unexpected dbType ${dbType.toString}")
        }
      }.getOrElse(Future.successful(ListMap()))
    }
  }

  private def getSqlValidValues(dataFilterInfoSettings: DataFilterInfoSettings): Future[ListMap[String, String]] = {
    queryExecutor.executeReadOnlyQuery[String, String](dataFilterInfoSettings.dbName)(dataFilterInfoSettings.validValuesQuery) match {
      case Success(data) => Future.successful(data)
      case Failure(t)    => throw new DataRetrievalException(s"Error on reading data from ${dataFilterInfoSettings.dbName}", t)
    }
  }

  private def getMongoValidValues(dataFilterInfoSettings: DataFilterInfoSettings): Future[ListMap[String, String]] = {
    val collectionName = dataFilterInfoSettings.collectionName.getOrElse(
      throw new DataRetrievalException(s"No mongo collection is specified for ${dataFilterInfoSettings.dbName}")
    )

    val pipelines = parseMongoAllowableConnectionsString(dataFilterInfoSettings.validValuesQuery).map(Document(_))
    val aggregationResultF = new MongoDbService(configuration, dataFilterInfoSettings.dbName).aggregate[ValidValues](collectionName, pipelines)

    aggregationResultF.map { aggregationResult =>
      ListMap(aggregationResult.map(validValues => validValues.value -> validValues.displayText): _*)
    }.recover {
      case t: Throwable =>
        throw new DataRetrievalException(s"Error on reading data from ${dataFilterInfoSettings.dbName}", t)
    }
  }

  private[services] def parseMongoAllowableConnectionsString(javascript: String): List[String] = {
    var count = 0
    var list = List[String]()
    var string: String = ""
    for (i <- javascript) {
      if (count != 0 || i == '{') {
        if (i == '{') count += 1 else if (i == '}') count -= 1
        string += i
        if (count == 0) {
          list :+= string
          string = ""
        }
      }
    }
    list
  }

  override protected implicit val mongoEntityFormat = MongoFormats.dataFilterInfoMongoFormat

  override protected def EntityName: String = "DataFilterInfo"

  override protected val filterFields: Set[String] = super.filterFields + "fieldName"

  case class ValidValues(_id: Option[String], value: String, displayText: String)
}
