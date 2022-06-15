package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{ and, equal }
import org.mongodb.scala.model.Updates.pushEach

import scala.concurrent.{ ExecutionContext, Future }

object AddTrainParametersToRfbNetDetector extends MongoMigration(
  "Add train parameters extract_from and top_k_selection into RFBNet detector",
  LocalDate.of(2019, Month.SEPTEMBER, 26)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val dcProjectPackages = db.getCollection("DCProjectPackages")
    val cvTLModelPrimitives = db.getCollection("CVTLModelPrimitives")

    val extractFrom = Document(
      "name" -> "extract_from",
      "description" -> "The index of layers in backbone_model from which features should be extracted",
      "multiple" -> false,
      "typeInfo" -> Document(
        "dataType" -> "STRING",
        "values" -> BsonArray("(15,22)", "(22,-1)"),
        "default" -> BsonArray("(22,-1)")
      ),
      "conditions" -> Document()
    )
    val topKSelection = Document(
      "name" -> "top_k_selection",
      "description" -> "Top_k number of boxes selected per class before nms",
      "multiple" -> false,
      "typeInfo" -> Document(
        "dataType" -> "INTEGER",
        "values" -> BsonArray(),
        "min" -> 1,
        "default" -> BsonArray(200)
      ),
      "conditions" -> Document()
    )

    for {
      systemPackages <- dcProjectPackages.find(
        equal("name", "deepcortex-ml-lib")
      ).toFuture
      _ = assert(systemPackages.length == 1, "Only one system package must exist")
      packageId = systemPackages.head.getObjectId("_id").toString
      _ <- cvTLModelPrimitives.updateOne(
        and(
          equal("packageId", packageId),
          equal("moduleName", "ml_lib.detectors.rfb_detector.RFBDetector"),
          equal("className", "RFBDetector")
        ),
        pushEach("params", extractFrom, topKSelection)
      ).toFuture
    } yield ()
  }
}
