package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.bson.types.ObjectId
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonString }

import scala.concurrent.{ ExecutionContext, Future }

object InsertValueIntoAutoEncoders extends MongoMigration (
  "insert value into autoEncoders",
  LocalDate.of(2019, Month.FEBRUARY, 6)
) {

  override def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val autoEncoders = db.getCollection("autoEncoders")
    val stackedDocument = Document(
      "_id" -> new ObjectId,
      "name" -> BsonString("STACKED"),
      "displayName" -> BsonString("STACKED AUTOENCODER"),
      "isNeural" -> BsonBoolean(false),
      "architectures" -> BsonArray(BsonString("SCAE"))
    )

    autoEncoders.insertOne(stackedDocument).toFuture().map(_ => ())

  }

}
