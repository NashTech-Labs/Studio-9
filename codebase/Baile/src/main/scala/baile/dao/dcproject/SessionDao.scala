package baile.dao.dcproject

import java.time.Instant

import baile.dao.dcproject.SessionDao.DCProjectIdIs
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.dcproject.Session
import org.mongodb.scala.bson.BsonString
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

object SessionDao {

  case class DCProjectIdIs(projectId: String) extends Filter

}

class SessionDao(
  protected val database: MongoDatabase
) extends MongoEntityDao[Session] {

  override val collectionName: String = "DCProjectSessions"

  override protected val fieldMapper: Map[Field, String] = Map.empty

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
    case DCProjectIdIs(projectId) => Try(Filters.equal("dcProjectId", projectId))
  }

  override protected[dcproject] def entityToDocument(entity: Session): Document = Document(
    "geminiSessionId" -> entity.geminiSessionId,
    "geminiSessionToken" -> entity.geminiSessionToken,
    "geminiSessionUrl" -> entity.geminiSessionUrl,
    "dcProjectId" -> entity.dcProjectId,
    "created" -> entity.created.toString
  )

  override protected[dcproject] def documentToEntity(document: Document): Try[Session] = Try {
    Session(
      geminiSessionId = document.getMandatory[BsonString]("geminiSessionId").getValue,
      geminiSessionToken = document.getMandatory[BsonString]("geminiSessionToken").getValue,
      geminiSessionUrl = document.getMandatory[BsonString]("geminiSessionUrl").getValue,
      dcProjectId = document.getMandatory[BsonString]("dcProjectId").getValue,
      created = Instant.parse(document.getMandatory[BsonString]("created").getValue)
    )
  }

}
