package baile.dao.project

import java.time.Instant
import java.util.UUID

import baile.dao.CommonSerializers
import baile.dao.images.AlbumDao.{ Created, Name, Updated }
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.WithId
import baile.daocommons.filters.{ Filter, IdIs }
import baile.daocommons.sorting.Field
import baile.domain.asset.AssetReference
import baile.domain.project.{ Folder, Project, ProjectAssetReference }
import baile.utils.TryExtensions._
import org.bson.types.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.{ BsonArray, BsonString }
import org.mongodb.scala.model.Updates.{ pull, push }
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try


object ProjectDao {

  case class NameIs(name: String) extends Filter
  case class OwnerIdIs(userId: UUID) extends Filter

}

class ProjectDao(protected val database: MongoDatabase) extends MongoEntityDao[Project] {

  import baile.dao.project.ProjectDao._

  override val collectionName: String = "projects"

  override protected val fieldMapper: Map[Field, String] =
    Map(
      Name -> "name",
      Created -> "created",
      Updated -> "updated"
    )

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
    case NameIs(name) => Try(MongoFilters.equal("name", name))
    case OwnerIdIs(ownerId) => Try(MongoFilters.equal("ownerId", ownerId.toString))
  }

  def addAsset(
    id: String,
    projectAssetReference: ProjectAssetReference
  )(implicit ec: ExecutionContext): Future[Option[WithId[Project]]] =
    executeUpdateAction(
      id,
      push("assets", projectFolderReferenceToDocument(projectAssetReference))
    )

  def removeAsset(
    id: String,
    assetReference: AssetReference
  )(implicit ec: ExecutionContext): Future[Option[WithId[Project]]] = executeUpdateAction(
    id,
    pull("assets", MongoFilters.and(
      MongoFilters.equal("assetReference.id", assetReference.id),
      MongoFilters.equal("assetReference.type", CommonSerializers.assetTypeToString(assetReference.`type`)))
    )
  )

  def removeAssetFromAllProjects(
    assetReference: AssetReference,
    ownerId: UUID
  )(implicit ec: ExecutionContext): Future[Unit] = {
    collection.updateMany(
      MongoFilters.equal("ownerId", ownerId.toString),
      pull("assets", MongoFilters.and(
        MongoFilters.equal("assetReference.id", assetReference.id),
        MongoFilters.equal("assetReference.type", CommonSerializers.assetTypeToString(assetReference.`type`)))
      )
    ).toFuture.map(_ => ())
  }

  def addFolder(
    id: String,
    folder: Folder
  )(implicit ec: ExecutionContext): Future[Option[WithId[Folder]]] = {
    val folderId = new ObjectId
    val folderWithId = WithId(folder, folderId.toString)
    executeUpdateAction(
      id,
      push("folders", folderToDocument(folderWithId))
    ).map(project => if (project.isDefined) Some(folderWithId) else None)
  }

  def removeFolder(
    id: String,
    folderId: String
  )(implicit ec: ExecutionContext): Future[Option[WithId[Project]]] = executeUpdateAction(
    id,
    pull("folders", MongoFilters.equal("folderId", folderId))
  )

  private def executeUpdateAction(
    id: String,
    updateAction: Bson
  )(implicit ec: ExecutionContext): Future[Option[WithId[Project]]] =
    for {
      predicate <- buildPredicate(IdIs(id)).toFuture
      updateResult <- collection.updateOne(predicate, updateAction).toFuture
      result <- {
        if (updateResult.getModifiedCount > 0) get(id)
        else Future.successful(None)
      }
    } yield result

  override protected[project] def entityToDocument(project: Project): Document = Document(
    "name" -> BsonString(project.name),
    "created" -> BsonString(project.created.toString),
    "updated" -> BsonString(project.updated.toString),
    "ownerId" -> BsonString(project.ownerId.toString),
    "folders" -> project.folders.map(folderToDocument),
    "assets" -> project.assets.map(projectFolderReferenceToDocument)
  )

  override protected[project] def documentToEntity(document: Document): Try[Project] = Try {
    Project(
      name = document.getMandatory[BsonString]("name").getValue,
      created = Instant.parse(document.getMandatory[BsonString]("created").getValue),
      updated = Instant.parse(document.getMandatory[BsonString]("updated").getValue),
      ownerId = UUID.fromString(document.getMandatory[BsonString]("ownerId").getValue),
      folders = document.getMandatory[BsonArray]("folders").map { elem =>
        documentToFolder(Document(elem.asDocument))
      },
      assets = document.getMandatory[BsonArray]("assets").map { elem =>
        documentToProjectAssetReference(Document(elem.asDocument))
      }
    )
  }

  private def documentToFolder(document: Document): WithId[Folder] = WithId(
    Folder(
      path = document.getMandatory[BsonString]("folderPath").getValue
    ),
    document.getMandatory[BsonString]("folderId").getValue
  )

  private def folderToDocument(folderWithId: WithId[Folder]): Document = Document(
    "folderId" -> folderWithId.id,
    "folderPath" -> folderWithId.entity.path
  )

  private def projectFolderReferenceToDocument(assetFolderReference: ProjectAssetReference): Document = Document(
    "assetReference" -> Document(
      "id" -> BsonString(assetFolderReference.assetReference.id),
      "type" -> BsonString(CommonSerializers.assetTypeToString(assetFolderReference.assetReference.`type`))
    ),
    "folderId" -> assetFolderReference.folderId.map(BsonString(_))
  )

  private def documentToProjectAssetReference(document: Document): ProjectAssetReference = {
    val assetReference = documentToAssetReference(document.getChildMandatory("assetReference"))
    val folderId = document.get[BsonString]("folderId").map(_.getValue)
    ProjectAssetReference(assetReference, folderId)
  }

  private def documentToAssetReference(document: Document): AssetReference =
    AssetReference(
      id = document.getMandatory[BsonString]("id").getValue,
      `type` = CommonSerializers.assetTypeFromString(document.getMandatory[BsonString]("type").getValue)
    )

}
