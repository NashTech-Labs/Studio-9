package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.bson.types.ObjectId
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonString }

import scala.concurrent.{ ExecutionContext, Future }

object InsertValueIntoLocalizers extends MongoMigration (
  "insert value into localizers",
  LocalDate.of(2019, Month.FEBRUARY, 6)
) {

  override def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val localizers = db.getCollection("localizers")
    val rfbNetDocument = Document(
      "_id" -> new ObjectId,
      "name" -> BsonString("RFBNet"),
      "displayName" -> BsonString("RFBNet"),
      "isNeural" -> BsonBoolean(true),
      "architectures" -> BsonArray(BsonString("VGG16_RFB"))
    )

    localizers.insertOne(rfbNetDocument).toFuture().map(_ => ())

  }

}
