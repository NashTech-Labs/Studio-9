package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.model.Filters.{ and, equal }
import org.mongodb.scala.model.UpdateOneModel
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object SetSpecificCategoryForSystemPipelineOperators extends MongoMigration(
  "Set a specific category for system pipeline operators",
  LocalDate.of(2019, Month.AUGUST, 14)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val pipelineOperators = db.getCollection("pipelineOperators")
    val dcProjectPackages = db.getCollection("DCProjectPackages")

    val systemPackagesWithCategory = Seq(
      ("deepcortex.pipelines.operators.load_album", "LoadAlbum", "SELECTOR"),
      ("deepcortex.pipelines.operators.load_table", "LoadTable", "SELECTOR"),
      ("deepcortex.pipelines.operators.save_album", "SaveAlbum", "SAVER"),
      ("deepcortex.pipelines.operators.save_table", "SaveTable", "SAVER"),
      ("deepcortex.pipelines.operators.transformations.resize_album", "ResizeAlbum", "ALBUM_TRANSFORMER"),
      ("deepcortex.pipelines.operators.transformations.fix_channels_album", "FixChannelsAlbum", "ALBUM_TRANSFORMER"),
      ("deepcortex.pipelines.operators.transformations.da.rotate_album", "RotateAlbum", "ALBUM_TRANSFORMER"),
      ("deepcortex.pipelines.operators.transformations.da.salt_album", "SaltAlbum", "ALBUM_TRANSFORMER")
    )

    for {
      systemPackages <- dcProjectPackages.find(
        equal("name", "deepcortex-ml-lib")
      ).toFuture
      _ = assert(systemPackages.length == 1, "Only one system package must exist")
      packageId = systemPackages.head.getObjectId("_id").toString
      _ <- pipelineOperators.bulkWrite(
        systemPackagesWithCategory.map { case (moduleName, className, category) =>
          UpdateOneModel(
            and(
              equal("packageId", packageId),
              equal("moduleName", moduleName),
              equal("className", className)
            ),
            set("category", category)
          )
        }
      ).toFuture()
    } yield ()
  }

}
