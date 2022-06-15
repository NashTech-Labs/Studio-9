package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.bson.types.ObjectId
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonString }

import scala.concurrent.{ ExecutionContext, Future }

object InsertValuesIntoClassifiers extends MongoMigration (
  "insert values into classifiers",
  LocalDate.of(2019, Month.FEBRUARY, 6)
) {

  override def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val classifiers = db.getCollection("classifiers")
    val fcn1Document = Document(
      "name" -> BsonString("FCN_1"),
      "displayName" -> BsonString("FCN 1-layer Classifier"),
      "isNeural" -> BsonBoolean(true),
      "architectures" -> BsonArray(
        BsonString("SCAE"),
        BsonString("VGG16"),
        BsonString("VGG16_RFB"),
        BsonString("SQUEEZENEXT"),
        BsonString("SQUEEZENEXT_REDUCED")
      )
    )
    val fcn2Document = Document(
      "name" -> BsonString("FCN_2"),
      "displayName" -> BsonString("FCN 2-layer Classifier"),
      "isNeural" -> BsonBoolean(true),
      "architectures" -> BsonArray(
        BsonString("SCAE"),
        BsonString("VGG16"),
        BsonString("VGG16_RFB"),
        BsonString("SQUEEZENEXT"),
        BsonString("SQUEEZENEXT_REDUCED")
      )
    )
    val fcn3Document = Document(
      "name" -> BsonString("FCN_3"),
      "displayName" -> BsonString("FCN 3-layer Classifier"),
      "isNeural" -> BsonBoolean(true),
      "architectures" -> BsonArray(
        BsonString("SCAE"),
        BsonString("VGG16"),
        BsonString("VGG16_RFB"),
        BsonString("SQUEEZENEXT"),
        BsonString("SQUEEZENEXT_REDUCED")
      )
    )
    val kpcaMnlDocument = Document(
      "name" -> BsonString("KPCA_MNL"),
      "displayName" -> BsonString("KPCA + MNL"),
      "isNeural" -> BsonBoolean(false),
      "architectures" -> BsonArray(
        BsonString("SCAE"),
        BsonString("VGG16"),
        BsonString("VGG16_RFB"),
        BsonString("SQUEEZENEXT"),
        BsonString("SQUEEZENEXT_REDUCED")
      )
    )
    val classifiersDocuments = Seq(
      fcn1Document,
      fcn2Document,
      fcn3Document,
      kpcaMnlDocument
    )
    val ids: Seq[ObjectId] = classifiersDocuments.map(_ => new ObjectId)
    val documents = classifiersDocuments.zip(ids).map {
      case (document, id) => document + ("_id" -> id)
    }

    classifiers.insertMany(documents).toFuture().map(_ => ())

  }

}
