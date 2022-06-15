package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonInt32, BsonString }
import org.mongodb.scala.model.Filters.{ and, equal }
import org.mongodb.scala.model.Updates.set

import scala.concurrent.{ ExecutionContext, Future }

object UpdateClassifiersWithPipelineParamsDefinition extends MongoMigration(
  "Update KPCA_MNL with PipelineParam definitions",
  LocalDate.of(2019, Month.APRIL, 17)
) {

  override private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {
    val pipelineOperators = db.getCollection("PipelineOperators")
    val kpcaMnlParams = BsonArray(
      Document(
        "name" -> BsonString("n_components"),
        "description" -> BsonString("Number of principal components"),
        "typeInfo" -> Document(
          "dataType" -> BsonString("INTEGER"),
          "min" -> BsonInt32(1),
          "max" -> BsonInt32(3000),
          "step" -> BsonInt32(1),
          "default" -> BsonArray(BsonInt32(600)),
          "values" -> BsonArray()
        ),
        "multiple" -> BsonBoolean(false),
        "conditions" -> Document()
      ),
      Document(
        "name" -> BsonString("kernel"),
        "description" -> BsonString("Kernel type"),
        "typeInfo" -> Document(
          "dataType" -> BsonString("STRING"),
          "values" -> BsonArray(
            BsonString("linear"),
            BsonString("poly"),
            BsonString("rbf"),
            BsonString("sigmoid"),
            BsonString("cosine"),
            BsonString("precomputed")
          ),
          "default" -> BsonArray(BsonString("poly"))
        ),
        "multiple" -> BsonBoolean(false),
        "conditions" -> Document()
      ),
      Document(
        "name" -> BsonString("degree"),
        "description" -> BsonString("Degree"),
        "typeInfo" -> Document(
          "dataType" -> BsonString("INTEGER"),
          "min" -> BsonInt32(1),
          "step" -> BsonInt32(1),
          "default" -> BsonArray(BsonInt32(3)),
          "values" -> BsonArray()
        ),
        "multiple" -> BsonBoolean(false),
        "conditions" -> Document(
          "kernel" -> Document(
            "dataType" -> BsonString("STRING"),
            "values" -> BsonArray(BsonString("poly"))
          )
        )
      )
    )

    val rpcaMnlParams = BsonArray(
      Document(
        "name" -> BsonString("n_components"),
        "description" -> BsonString("Number of principal components"),
        "typeInfo" -> Document(
          "dataType" -> BsonString("INTEGER"),
          "min" -> BsonInt32(1),
          "max" -> BsonInt32(3000),
          "step" -> BsonInt32(1),
          "default" -> BsonArray(BsonInt32(600)),
          "values" -> BsonArray()
        ),
        "multiple" -> BsonBoolean(false),
        "conditions" -> Document()
      )
    )

    for {
      _ <- pipelineOperators.updateOne(and(equal("name", "KPCA_MNL")), set("params", kpcaMnlParams)).toFuture()
      _ <- pipelineOperators.updateOne(and(equal("name", "RPCA_MNL")), set("params", rpcaMnlParams)).toFuture()
    } yield ()
  }

}
