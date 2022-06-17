package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import baile.dao.mongo.migrations.MongoMigration.createCollection
import org.mongodb.scala.bson.collection.mutable.Document
import org.mongodb.scala.model.Updates.rename
import org.mongodb.scala.{ MongoDatabase, MongoNamespace }

import scala.concurrent.{ ExecutionContext, Future }

object RenamePipelineOperatorsToCVTLModelPrimitivesAndCreateNewCollectionsForPipeline extends MongoMigration(
  "Rename PipelineOperators to CVTLModelPrimitives and create Pipelines and PipelineOperators collections",
  date = LocalDate.of(2019, Month.MAY, 20)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val oldPipelineOperators = db.getCollection("PipelineOperators")
    for {
      _ <- oldPipelineOperators.updateMany(Document.empty,
        rename("operatorType", "cvTLModelPrimitiveType")
      ).toFuture
      _ <- oldPipelineOperators.renameCollection(MongoNamespace(db.name, "CVTLModelPrimitives")).toFuture()
      _ <- createCollection("pipelineOperators")
      _ <- createCollection("pipelines")
    } yield ()
  }

}

