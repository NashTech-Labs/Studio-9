package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.mutable.Document

import scala.concurrent.{ ExecutionContext, Future }

object CreateAlbumIdIndexForPictures extends MongoMigration(
  "Create albumId index for pictures",
  date = LocalDate.of(2019, Month.MARCH, 20)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val pictures = db.getCollection("pictures")
    pictures.createIndex(Document {
      "albumId" -> 1
    }).toFuture.map(_ => ())
  }

}
