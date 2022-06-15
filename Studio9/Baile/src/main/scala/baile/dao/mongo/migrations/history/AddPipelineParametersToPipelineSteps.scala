package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object AddPipelineParametersToPipelineSteps extends MongoMigration(
  "Add pipeline parameters to pipeline steps",
  date = LocalDate.of(2019, Month.SEPTEMBER, 25)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val pipelinesCollection = db.getCollection("pipelines")

    for {
      pipelines <- pipelinesCollection.find().toFuture()
      _ <- Future.traverse(pipelines) { pipeline =>
        val updatedSteps = pipeline
          .getMandatory[BsonArray]("steps")
          .map(_.asDocument().append("pipelineParameters", Document()))

        pipelinesCollection.updateOne(
          Document("_id" -> pipeline.getObjectId("_id")),
          set("steps", updatedSteps)
        ).toFuture()
      }
    } yield ()
  }
}
