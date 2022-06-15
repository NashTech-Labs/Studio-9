package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.mutable.Document

import scala.concurrent.{ ExecutionContext, Future }

object CreateModuleNameIndexOnPipelineOperator extends MongoMigration(
  "Create moduleName index on PiplelineOperator",
  LocalDate.of(2019, Month.APRIL, 24)
) {

  override def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val operators = db.getCollection("PipelineOperators")
    operators.createIndex(Document {
      "moduleName" -> 1
    }).toFuture.map(_ => ())
  }

}
