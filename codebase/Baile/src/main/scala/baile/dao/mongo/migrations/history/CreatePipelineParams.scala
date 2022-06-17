package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters.{ exists, not }
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object CreatePipelineParams extends MongoMigration(
  "Create pipeline params for CVModel and CVFeatureExtractor",
  date = LocalDate.of(2019, Month.FEBRUARY, 28)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val cvFeatureExtractors = db.getCollection("CVFeatureExtractors")
    val cvModels = db.getCollection("CVModels")

    for {
      _ <- cvFeatureExtractors.updateMany(not(exists("params")), set("params", BsonDocument())).toFuture()
      _ <- cvModels.updateMany(not(exists("params")), set("params", BsonDocument())).toFuture()
    } yield ()
  }
}
