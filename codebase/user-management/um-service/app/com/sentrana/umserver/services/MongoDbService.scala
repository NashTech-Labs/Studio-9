package com.sentrana.umserver.services

import java.util.UUID
import javax.inject.Inject
import javax.net.ssl.SSLSocketFactory
import scala.collection.JavaConverters._

import com.mongodb.MongoCredential
import org.mongodb.scala.{ Document, Completed, FindObservable, MongoClient, MongoClientSettings, MongoCollection, MongoDatabase, Observable, Observer, ReadPreference, ServerAddress }
import org.mongodb.scala.connection.ClusterSettings
import com.mongodb.MongoCredential._
import org.mongodb.scala.connection.{ NettyStreamFactoryFactory, SslSettings }
import com.mongodb.ConnectionString
import com.sentrana.umserver.entities.MongoEntityFormat
import com.sentrana.umserver.exceptions.UpdateFailedException
import com.sentrana.umserver.shared.dtos.WithId
import org.bson.conversions.Bson
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.libs.json.{ Json, Reads }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Paul Lysak on 12.04.16.
 */
class MongoDbService @Inject() (configuration: Configuration, dbName: String) {
  private val _mongodbUri = configuration.getString(s"mongodb.${dbName}.uri").getOrElse("mongodb://localhost")
  private lazy val mongoClient = MongoClient(_mongodbUri)
  private lazy val _dbName = configuration.getString(s"mongodb.${dbName}.db").orElse({
    Option((new ConnectionString(_mongodbUri)).getDatabase)
  }).getOrElse("um-service")
  private lazy val db = mongoClient.getDatabase(_dbName)

  //TODO introduce org scope for better safety
  def save[E](entity: E)(implicit fmt: MongoEntityFormat[E]): Future[E] = withCollection { col =>
    col.insertOne(fmt.toDocument(entity)).toFuture().map(_ => entity)
  }

  def get[E](id: String, orgScope: OrgScope = OrgScopeRoot)(implicit fmt: MongoEntityFormat[E]): Future[Option[E]] = withCollection { col =>
    col.find(byId(id, orgScope)).toFuture().map({ docs =>
      docs.headOption.map(fmt.fromDocument)
    })
  }

  /**
   * Intentionally no default scope to make sure that caller knows what's safe to update now
   *
   * @param entity
   * @param orgScope
   * @param fmt
   * @tparam E
   * @return
   */
  def update[E <: WithId](entity: E, orgScope: OrgScope)(implicit fmt: MongoEntityFormat[E]): Future[E] = withCollection { col =>
    col.replaceOne(byId(entity.id, orgScope), fmt.toDocument(entity)).toFuture().
      map(res => if (res.headOption.exists(_.getModifiedCount < 1))
        throw new UpdateFailedException(s"Collection ${fmt.collectionName} item ${entity.id} wasn't updated, perhaps it's not in current scope")
      else entity)
  }

  /**
   * Intentionally no default scope to make sure that caller knows what's safe to delete now
   *
   * @param id
   * @param orgScope
   * @param fmt
   * @tparam E
   * @return
   */
  def delete[E](id: String, orgScope: OrgScope)(implicit fmt: MongoEntityFormat[E]): Future[_] = withCollection { col =>
    col.deleteOne(byId(id, orgScope)).toFuture()
  }

  def findSingle[E](criteria: Bson)(implicit fmt: MongoEntityFormat[E]): Future[Option[E]] = {
    find(criteria, limit = 1).toFuture().map(_.headOption)
  }

  def find[E](criteria: Bson, sort: Option[Bson] = None, offset: Int = 0, limit: Int = 10)(implicit fmt: MongoEntityFormat[E]): Observable[E] = withCollection { col =>
    val o1 = col.find(criteria).skip(offset)
    val sortedResult = sort.map(o1.sort).getOrElse(o1)
    (if (limit > 0) sortedResult.limit(limit) else sortedResult).map(fmt.fromDocument)
  }

  def count[E](criteria: Bson)(implicit fmt: MongoEntityFormat[E]): Future[Long] = withCollection { col =>
    col.count(criteria).toFuture().map(_.headOption.getOrElse(0l))
  }

  def generateId: String = UUID.randomUUID().toString

  def aggregate[E](collectionName: String, pipeline: Seq[Bson])(implicit jsonReads: Reads[E]): Future[Seq[E]] = {
    val collection = db.getCollection(collectionName)
    collection.aggregate(pipeline).toFuture().map(documents => documents.map(doc => Json.parse(doc.toJson()).as[E]))
  }

  private def withCollection[E, T](block: MongoCollection[Document] => T)(implicit fmt: MongoEntityFormat[E]) = {
    val col = db.getCollection(fmt.collectionName)
    block(col)
  }

  private def byId(id: String, orgScope: OrgScope = OrgScopeRoot) = {
    import org.mongodb.scala.model.Filters._
    orgScope.withOrgFilter(equal("id", id))
  }

  private lazy val log = LoggerFactory.getLogger(classOf[MongoDbService])
}
