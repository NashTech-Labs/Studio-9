package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.bson.{ BsonDocument, BsonString }
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.Filters._

import scala.concurrent.{ ExecutionContext, Future }

object SetClassReferenceForTabularModel extends MongoMigration(
  "Set ClassReference field for tabular model",
  date = LocalDate.of(2019, Month.MAY, 14)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val dcProjectPackages = db.getCollection("DCProjectPackages")
    val tabularModels = db.getCollection("tabularModels")

    for {
      systemPackages <- dcProjectPackages.find(
        equal("name", "deepcortex-ml-lib")
      ).toFuture
      _ = assert(systemPackages.length == 1, "Only one system package exists")
      systemPackage = systemPackages.head
      classReference = BsonDocument(
        "moduleName" -> BsonString("ml_lib.tabular.fused_pipeline_stage"),
        "className" -> BsonString("FusedTabularPipeline"),
        "packageId" -> BsonString(systemPackage.getObjectId("_id").toString)
      )
      _ <- tabularModels.updateMany(
        org.mongodb.scala.bson.collection.immutable.Document(),
        set("classReference", classReference)
      ).toFuture
    } yield ()
  }

}
