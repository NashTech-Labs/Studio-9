package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.{ BsonArray, BsonDocument, BsonNull, BsonString, BsonValue }
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.Filters.equal

import scala.concurrent.{ ExecutionContext, Future }

object MergeCVFeatureExtractorsToCVModels extends MongoMigration(
  "Merge CV feature extractors to CV models",
  date = LocalDate.of(2018, Month.DECEMBER, 18)
) {

  // scalastyle:off method.length
  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {

    def buildModelReferenceDocument(oldDocument: Document): BsonValue =
      (oldDocument.get[BsonString]("cortexId"), oldDocument.get[BsonString]("cortexFilePath")) match {
        case (Some(cortexId), Some(cortexFilePath)) => BsonDocument(
          "cortexId" -> cortexId,
          "cortexFilePath" -> cortexFilePath
        )
        case _ => BsonNull()
      }

    def convertOldModelTypeToNewModelType(oldDocument: Document): Document = {
      val newInnerTypeField = oldDocument.get[BsonString]("labelMode").get.getValue match {
        case "CLASSIFICATION" => "CLASSIFIER"
        case "LOCALIZATION" => "LOCALIZER"
      }
      oldDocument + ("type" -> newInnerTypeField)
    }

    def convertFeatureExtractorToCVModel(oldFEDocument: Document): Document = {

      val modelTypeDocument = oldFEDocument.get[BsonString]("architecture").get.getValue match {
        // These do not have consumer set
        case "STACKED_AUTOENCODER" => Document(
          "type" -> "AUTO_ENCODER",
          "name" -> "STACKED"
        )
        // These should have consumer set
        case _ => convertOldModelTypeToNewModelType(oldFEDocument.getChildMandatory("consumer"))
      }

      oldFEDocument ++ Document(
        "cortexFeatureExtractorReference" -> buildModelReferenceDocument(oldFEDocument),
        "cortexModelReference" -> BsonNull(),
        "type" -> modelTypeDocument,
        "featureExtractorArchitecture" -> oldFEDocument.get("architecture"),
        "featureExtractorId" -> BsonNull()
      )
    }

    def convertOldCVModelToNewCVModel(oldModelDocument: Document): Document =
      oldModelDocument ++ Document(
        "cortexModelReference" -> buildModelReferenceDocument(oldModelDocument),
        "type" -> convertOldModelTypeToNewModelType(oldModelDocument.getChildMandatory("type"))
      )

    def convertOldProjectToNewProject(oldProjectDocument: Document): Document = {
      val newAssetsArray = oldProjectDocument.getMandatory[BsonArray]("assets").map { element =>
        val assetDocument = Document(element.asDocument)
        val oldAssetReferenceDocument = assetDocument.getChildMandatory("assetReference")
        val oldAssetType = oldAssetReferenceDocument.getMandatory[BsonString]("type").getValue
        val newAssetType = if (oldAssetType == "CV_FEATURE_EXTRACTOR") "CV_MODEL" else oldAssetType
        val newAssetReferenceDocument = oldAssetReferenceDocument.updated("type", BsonString(newAssetType))
        assetDocument.updated("assetReference", newAssetReferenceDocument)
      }
      oldProjectDocument.updated("assets", newAssetsArray)
    }

    val cvModels = db.getCollection("CVModels")
    val cvFeatureExtractors = db.getCollection("CVFeatureExtractors")
    val sharedResources = db.getCollection("sharedResources")
    val processes = db.getCollection("processes")
    val projects = db.getCollection("projects")

    val cvModelsMigrateResult = for {
      oldModelDocument <- cvModels.find()
      newModelDocument = convertOldCVModelToNewCVModel(oldModelDocument)
      result <- cvModels.replaceOne(
        Document("_id" -> oldModelDocument.getObjectId("_id")),
        newModelDocument
      )
    } yield result

    val cvFeatureExtractorsMigrateResult = for {
      oldFEDocument <- cvFeatureExtractors.find()
      newFEDocument = convertFeatureExtractorToCVModel(oldFEDocument)
      result <- cvModels.insertOne(newFEDocument)
    } yield result

    val sharedResourcesMigrateResult = sharedResources.updateMany(
      equal("assetType", BsonString("CV_FEATURE_EXTRACTOR")),
      set("assetType", BsonString("CV_MODEL"))
    )

    val processesMigrateResult = processes.updateMany(
      equal("targetType", BsonString("CV_FEATURE_EXTRACTOR")),
      set("targetType", BsonString("CV_MODEL"))
    )

    val projectsMigrateResult = for {
      oldProjectDocument <- projects.find(Document())
      newProjectDocument = convertOldProjectToNewProject(oldProjectDocument)
      result <- projects.replaceOne(
        Document("_id" -> oldProjectDocument.getObjectId("_id")),
        newProjectDocument
      )
    } yield result

    for {
      _ <- cvModelsMigrateResult.toFuture
      _ <- cvFeatureExtractorsMigrateResult.toFuture
      _ <- sharedResourcesMigrateResult.toFuture
      _ <- processesMigrateResult.toFuture
      _ <- projectsMigrateResult.toFuture
    } yield ()

  }
  // scalastyle:on method.length

}
