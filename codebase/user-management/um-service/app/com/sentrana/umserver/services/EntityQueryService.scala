package com.sentrana.umserver.services

import com.sentrana.umserver.entities.{ ApplicationInfoEntity, MongoEntityFormat }
import com.sentrana.umserver.exceptions.ItemNotFoundException
import com.sentrana.umserver.shared.dtos.WithId
import com.sentrana.umserver.shared.dtos.enums.SortOrder
import com.sentrana.umserver.utils.MongoQueryUtil
import org.bson.conversions.Bson
import org.mongodb.scala.bson.collection.immutable.Document

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by Paul Lysak on 17.06.16.
 */
trait EntityQueryService[ENTITY <: WithId] {

  protected implicit val mongoEntityFormat: MongoEntityFormat[ENTITY]

  protected def mongoDbService: MongoDbService

  def get(id: String): Future[Option[ENTITY]] =
    mongoDbService.get[ENTITY](id)

  def find(params: Map[String, Seq[String]] = Map.empty, sortParams: Map[String, SortOrder] = Map.empty, offset: Int = 0, limit: Int = 10): Future[Seq[ENTITY]] = {
    mongoDbService.find[ENTITY](
      MongoQueryUtil.buildFilterDoc(params, filterFields),
      MongoQueryUtil.buildSortDocOpt(sortParams, sortFields),
      offset,
      limit
    ).toFuture()
  }

  def count(params: Map[String, Seq[String]] = Map.empty): Future[Long] = {
    mongoDbService.count[ENTITY](MongoQueryUtil.buildFilterDoc(params, filterFields))
  }

  def getMandatory(id: String): Future[ENTITY] =
    get(id).map(_.getOrElse(throw new ItemNotFoundException(s"$EntityName with id=$id not found")))

  protected def filterFields: Set[String] = Set("id")

  protected def sortFields: Set[String] = Set()

  protected def EntityName: String
}
