package baile.dao.mongo.migrations.history

import java.time.{ LocalDate, Month }

import baile.dao.mongo.migrations.MongoMigration
import org.bson.types.ObjectId
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonDouble, BsonString }
import org.mongodb.scala.model.Filters._

import scala.concurrent.{ ExecutionContext, Future }

object InsertSystemPipelineOperators extends MongoMigration(
  "Insert system predefined pipeline operators",
  LocalDate.of(2019, Month.JUNE, 10)
) {

  // scalastyle:off method.length
  override def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {

    val albumDataType = Document(
      "type" -> BsonString("COMPLEX"),
      "value" -> Document(
        "definition" -> BsonString("deepcortex.library.albums.Album"),
        "parents" -> BsonArray(
          Document(
            "definition" -> BsonString("deepcortex.internal_api.baile.model.album.Album"),
            "parents" -> BsonArray(
              Document(
                "definition" -> BsonString("deepcortex.internal_api.baile.model.base.BaseModel")
              )
            )
          )
        )
      )
    )
    val tableDataType = Document(
      "type" -> BsonString("COMPLEX"),
      "value" -> Document(
        "definition" -> BsonString("deepcortex.library.tables.Table"),
        "parents" -> BsonArray(
          Document(
            "definition" -> BsonString("deepcortex.internal_api.baile.model.table.Table"),
            "parents" -> BsonArray(
              Document(
                "definition" -> BsonString("deepcortex.internal_api.baile.model.base.BaseModel")
              )
            )
          )
        )
      )
    )

    val loadAlbum = { packageId: String =>
      Document(
        "_id" -> new ObjectId,
        "packageId" -> BsonString(packageId),
        "name" -> BsonString("Load Album"),
        "moduleName" -> BsonString("deepcortex.pipelines.operators.load_album"),
        "className" -> BsonString("LoadAlbum"),
        "inputs" -> BsonArray(),
        "outputs" -> BsonArray(
          Document(
            "type" -> albumDataType
          )
        ),
        "params" -> BsonArray(
          Document(
            "name" -> BsonString("album_id"),
            "typeInfo" -> Document(
              "assetType" -> BsonString("ALBUM"),
              "dataType" -> BsonString("ASSET")
            ),
            "multiple" -> BsonBoolean(false),
            "conditions" -> Document()
          )
        )
      )
    }

    val loadTable = { packageId: String =>
      Document(
        "_id" -> new ObjectId,
        "packageId" -> BsonString(packageId),
        "name" -> BsonString("Load Table"),
        "moduleName" -> BsonString("deepcortex.pipelines.operators.load_table"),
        "className" -> BsonString("LoadTable"),
        "inputs" -> BsonArray(),
        "outputs" -> BsonArray(
          Document(
            "type" -> tableDataType
          )
        ),
        "params" -> BsonArray(
          Document(
            "name" -> BsonString("table_id"),
            "typeInfo" -> Document(
              "assetType" -> BsonString("TABLE"),
              "dataType" -> BsonString("ASSET")
            ),
            "multiple" -> BsonBoolean(false),
            "conditions" -> Document()
          )
        )
      )
    }

    val saveAlbum = { packageId: String =>
      Document(
        "_id" -> new ObjectId,
        "packageId" -> BsonString(packageId),
        "name" -> BsonString("Save Album"),
        "moduleName" -> BsonString("deepcortex.pipelines.operators.save_album"),
        "className" -> BsonString("SaveAlbum"),
        "inputs" -> BsonArray(
          Document(
            "name" -> BsonString("album"),
            "description" -> BsonString("Loaded album"),
            "covariate" -> BsonBoolean(true),
            "type" -> albumDataType
          )
        ),
        "outputs" -> BsonArray(
          Document(
            "description" -> BsonString("Saved album id"),
            "type" -> Document(
              "type" -> BsonString("PRIMITIVE"),
              "value" -> BsonString("STRING")
            )
          )
        ),
        "params" -> BsonArray(
          Document(
            "name" -> BsonString("name"),
            "description" -> BsonString("Album name"),
            "typeInfo" -> Document(
              "dataType" -> BsonString("STRING"),
              "values" -> BsonArray(),
              "default" -> BsonArray()
            ),
            "multiple" -> BsonBoolean(false),
            "conditions" -> Document()
          ),
          Document(
            "name" -> BsonString("description"),
            "description" -> BsonString("Album description"),
            "typeInfo" -> Document(
              "dataType" -> BsonString("STRING"),
              "values" -> BsonArray(),
              "default" -> BsonArray()
            ),
            "multiple" -> BsonBoolean(false),
            "conditions" -> Document()
          )
        )
      )
    }

    val saveTable = { packageId: String =>
      Document(
        "_id" -> new ObjectId,
        "packageId" -> BsonString(packageId),
        "name" -> BsonString("Save Table"),
        "moduleName" -> BsonString("deepcortex.pipelines.operators.save_table"),
        "className" -> BsonString("SaveTable"),
        "inputs" -> BsonArray(
          Document(
            "name" -> BsonString("table"),
            "description" -> BsonString("Loaded table"),
            "covariate" -> BsonBoolean(true),
            "type" -> tableDataType
          )
        ),
        "outputs" -> BsonArray(
          Document(
            "description" -> BsonString("Saved table id"),
            "type" -> Document(
              "type" -> BsonString("PRIMITIVE"),
              "value" -> BsonString("STRING")
            )
          )
        ),
        "params" -> BsonArray(
          Document(
            "name" -> BsonString("name"),
            "description" -> BsonString("Table name"),
            "typeInfo" -> Document(
              "dataType" -> BsonString("STRING"),
              "values" -> BsonArray(),
              "default" -> BsonArray()
            ),
            "multiple" -> BsonBoolean(false),
            "conditions" -> Document()
          ),
          Document(
            "name" -> BsonString("description"),
            "description" -> BsonString("Table description"),
            "typeInfo" -> Document(
              "dataType" -> BsonString("STRING"),
              "values" -> BsonArray(),
              "default" -> BsonArray()
            ),
            "multiple" -> BsonBoolean(false),
            "conditions" -> Document()
          )
        )
      )
    }

    val resizeAlbum = { packageId: String =>
      Document(
        "_id" -> new ObjectId,
        "packageId" -> BsonString(packageId),
        "name" -> BsonString("Resize transformation applied to Album"),
        "moduleName" -> BsonString("deepcortex.pipelines.operators.transformations.resize_album"),
        "className" -> BsonString("ResizeAlbum"),
        "inputs" -> BsonArray(
          Document(
            "name" -> BsonString("album"),
            "description" -> BsonString("Loaded album"),
            "covariate" -> BsonBoolean(true),
            "type" -> albumDataType
          )
        ),
        "outputs" -> BsonArray(
          Document(
            "type" -> albumDataType
          )
        ),
        "params" -> BsonArray(
          Document(
            "name" -> BsonString("target_size"),
            "typeInfo" -> Document(
              "dataType" -> BsonString("INTEGER"),
              "values" -> BsonArray(),
              "default" -> BsonArray()
            ),
            "multiple" -> BsonBoolean(false),
            "conditions" -> Document()
          )
        )
      )
    }

    val fixChannelsAlbum = { packageId: String =>
      Document(
        "_id" -> new ObjectId,
        "packageId" -> BsonString(packageId),
        "name" -> BsonString("Fix channels transformation applied to Album"),
        "moduleName" -> BsonString("deepcortex.pipelines.operators.transformations.fix_channels_album"),
        "className" -> BsonString("FixChannelsAlbum"),
        "inputs" -> BsonArray(
          Document(
            "name" -> BsonString("album"),
            "description" -> BsonString("Loaded album"),
            "covariate" -> BsonBoolean(true),
            "type" -> albumDataType
          )
        ),
        "outputs" -> BsonArray(
          Document(
            "type" -> albumDataType
          )
        ),
        "params" -> BsonArray(
          Document(
            "name" -> BsonString("num_channels"),
            "typeInfo" -> Document(
              "dataType" -> BsonString("INTEGER"),
              "values" -> BsonArray(),
              "default" -> BsonArray()
            ),
            "multiple" -> BsonBoolean(false),
            "conditions" -> Document()
          )
        )
      )
    }

    val rotateAlbum = { packageId: String =>
      Document(
        "_id" -> new ObjectId,
        "packageId" -> BsonString(packageId),
        "name" -> BsonString("DA trasformation: rotate album"),
        "moduleName" -> BsonString("deepcortex.pipelines.operators.transformations.da.rotate_album"),
        "className" -> BsonString("RotateAlbum"),
        "inputs" -> BsonArray(
          Document(
            "name" -> BsonString("album"),
            "description" -> BsonString("Loaded album"),
            "covariate" -> BsonBoolean(true),
            "type" -> albumDataType
          )
        ),
        "outputs" -> BsonArray(
          Document(
            "type" -> albumDataType
          )
        ),
        "params" -> BsonArray(
          Document(
            "name" -> BsonString("degrees"),
            "typeInfo" -> Document(
              "dataType" -> BsonString("INTEGER"),
              "values" -> BsonArray(),
              "default" -> BsonArray()
            ),
            "multiple" -> BsonBoolean(false),
            "conditions" -> Document()
          )
        )
      )
    }

    val saltAlbum = { packageId: String =>
      Document(
        "_id" -> new ObjectId,
        "packageId" -> BsonString(packageId),
        "name" -> BsonString("DA trasformation: salt album"),
        "moduleName" -> BsonString("deepcortex.pipelines.operators.transformations.da.salt_album"),
        "className" -> BsonString("SaltAlbum"),
        "inputs" -> BsonArray(
          Document(
            "name" -> BsonString("album"),
            "description" -> BsonString("Loaded album"),
            "covariate" -> BsonBoolean(true),
            "type" -> albumDataType
          )
        ),
        "outputs" -> BsonArray(
          Document(
            "type" -> albumDataType
          )
        ),
        "params" -> BsonArray(
          Document(
            "name" -> BsonString("knockout_fraction"),
            "typeInfo" -> Document(
              "dataType" -> BsonString("FLOAT"),
              "min" -> BsonDouble(0),
              "max" -> BsonDouble(0.99),
              "values" -> BsonArray(),
              "default" -> BsonArray()
            ),
            "multiple" -> BsonBoolean(false),
            "conditions" -> Document()
          ),
          Document(
            "name" -> BsonString("pepper_probability"),
            "typeInfo" -> Document(
              "dataType" -> BsonString("FLOAT"),
              "min" -> BsonDouble(0),
              "max" -> BsonDouble(1),
              "values" -> BsonArray(),
              "default" -> BsonArray(BsonDouble(0))
            ),
            "multiple" -> BsonBoolean(false),
            "conditions" -> Document()
          )
        )
      )
    }

    val operators = { packageId: String =>
      Seq(
        loadAlbum,
        loadTable,
        saveAlbum,
        saveTable,
        resizeAlbum,
        fixChannelsAlbum,
        rotateAlbum,
        saltAlbum
      ).map(_ (packageId))
    }

    val pipelineOperators = db.getCollection("pipelineOperators")
    val dcProjectPackages = db.getCollection("DCProjectPackages")

    for {
      systemPackages <- dcProjectPackages.find(
        equal("name", "deepcortex-ml-lib")
      ).toFuture
      _ = assert(systemPackages.length == 1, "Only one system package must exist")
      packageDocument = systemPackages.head
      _ <- pipelineOperators.insertMany(operators(packageDocument.getObjectId("_id").toString)).toFuture
    } yield ()
  }

  // scalastyle:off method.length

}
