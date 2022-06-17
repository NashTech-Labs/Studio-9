package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.model.Filters.{ and, equal }
import org.mongodb.scala.model.UpdateOneModel
import org.mongodb.scala.model.Updates._

import scala.concurrent.{ ExecutionContext, Future }

object RenameSystemPipelineOperatorsLoadingAssets extends MongoMigration(
  "Rename system pipeline operators which load assets",
  LocalDate.of(2019, Month.NOVEMBER, 13)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val pipelineOperators = db.getCollection("pipelineOperators")
    val dcProjectPackages = db.getCollection("DCProjectPackages")

    val operatorsToRename = Seq(
      ("deepcortex.pipelines.operators.load_album", "LoadAlbum",
        "deepcortex.pipelines.operators.select_album", "SelectAlbum", "Select Album"),
      ("deepcortex.pipelines.operators.load_table", "LoadTable",
        "deepcortex.pipelines.operators.select_table", "SelectTable", "Select Table")
    )

    for {
      systemPackages <- dcProjectPackages.find(
        equal("name", "deepcortex-ml-lib")
      ).toFuture
      _ = assert(systemPackages.length == 1, "Only one system package must exist")
      packageId = systemPackages.head.getObjectId("_id").toString
      _ <- pipelineOperators.bulkWrite(
        operatorsToRename.map { case (moduleName, className, newModuleName, newClassName, newName) =>
          UpdateOneModel(
            and(
              equal("packageId", packageId),
              equal("moduleName", moduleName),
              equal("className", className)
            ),
            combine(
              set("moduleName", newModuleName),
              set("className", newClassName),
              set("name", newName)
            )
          )
        }
      ).toFuture()
    } yield ()
  }

}

