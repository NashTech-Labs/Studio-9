package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.bson.types.ObjectId
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{ BsonBoolean, BsonString }

import scala.concurrent.{ ExecutionContext, Future }

object InsertValuesIntoFEArchitectures extends MongoMigration (
  "insert values into FEArchitectures",
  LocalDate.of(2019, Month.FEBRUARY, 6)
) {

  override def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val feArchitectures = db.getCollection("FEArchitectures")
    val scaeDocument = Document(
      "name" -> BsonString("SCAE"),
      "displayName" -> BsonString("Stacked AutoEncoder"),
      "needsConsumer" -> BsonBoolean(false)
    )
    val vgg16Document = Document(
      "name" -> BsonString("VGG16"),
      "displayName" -> BsonString("VGG 16"),
      "needsConsumer" -> BsonBoolean(true)
    )
    val vgg16RFBDocument = Document(
      "name" -> BsonString("VGG16_RFB"),
      "displayName" -> BsonString("VGG 16 for RFBNet"),
      "needsConsumer" -> BsonBoolean(true)
    )
    val squeezeNextDocument = Document(
      "name" -> BsonString("SQUEEZENEXT"),
      "displayName" -> BsonString("SqueezeNext"),
      "needsConsumer" -> BsonBoolean(true)
    )
    val squeezeNextReducedDocument = Document(
      "name" -> BsonString("SQUEEZENEXT_REDUCED"),
      "displayName" -> BsonString("SqueezeNext Reduced"),
      "needsConsumer" -> BsonBoolean(true)
    )
    val architectures = Seq(
      scaeDocument,
      vgg16Document,
      vgg16RFBDocument,
      squeezeNextDocument,
      squeezeNextReducedDocument
    )
    val ids: Seq[ObjectId] = architectures.map(_ => new ObjectId)
    val documents = architectures.zip(ids).map {
      case (document, id) => document + ("_id" -> id)
    }

    feArchitectures.insertMany(documents).toFuture().map(_ => ())

  }

}
