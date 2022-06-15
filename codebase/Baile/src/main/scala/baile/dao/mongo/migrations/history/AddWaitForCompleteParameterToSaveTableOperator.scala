package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{ and, equal }
import org.mongodb.scala.model.Updates.push

import scala.concurrent.{ ExecutionContext, Future }

object AddWaitForCompleteParameterToSaveTableOperator extends MongoMigration(
  "Add wait_for_complete parameter to SaveTable operator",
  LocalDate.of(2019, Month.NOVEMBER, 22)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val pipelineOperators = db.getCollection("pipelineOperators")
    val dcProjectPackages = db.getCollection("DCProjectPackages")

    for {
      systemPackages <- dcProjectPackages.find(
        equal("name", "deepcortex-ml-lib")
      ).toFuture
      _ = assert(systemPackages.length == 1, "Only one system package must exist")
      packageId = systemPackages.head.getObjectId("_id").toString
      _ <- pipelineOperators.updateOne(
        and(
          equal("packageId", packageId),
          equal("moduleName", "deepcortex.pipelines.operators.save_table"),
          equal("className", "SaveTable")
        ),
        push(
          "params",
          Document(
            "name" -> "wait_for_complete",
            "description" -> "Wait until table loading is completed",
            "multiple" -> false,
            "typeInfo" -> Document(
              "dataType" -> "BOOLEAN",
              "default" -> BsonArray(false)
            ),
            "conditions" -> Document()
          )
        )
      ).toFuture
    } yield ()
  }

}

