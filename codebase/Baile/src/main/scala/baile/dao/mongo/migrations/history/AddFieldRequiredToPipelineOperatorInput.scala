package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean }
import org.mongodb.scala.model.Updates._

import scala.concurrent.{ ExecutionContext, Future }

object AddFieldRequiredToPipelineOperatorInput extends MongoMigration(
  "Add required field in existing pipeline operators input and set it to true",
  date = LocalDate.of(2019, Month.SEPTEMBER, 10)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val pipelineOperators = db.getCollection("pipelineOperators")

    def updateInputDocument(document: Document) = {
      document + ("required" -> BsonBoolean(true))
    }

    pipelineOperators
      .find()
      .flatMap { operator =>
        val oldInputs = operator.getMandatory[BsonArray]("inputs")
        val newInputs = oldInputs.map { oldInput =>
          updateInputDocument(oldInput.asDocument())
        }
        pipelineOperators.updateOne(
          Document("_id" -> operator.getObjectId("_id")),
          set("inputs", newInputs)
        )
      }
      .toFuture
      .map(_ => ())
  }

}
