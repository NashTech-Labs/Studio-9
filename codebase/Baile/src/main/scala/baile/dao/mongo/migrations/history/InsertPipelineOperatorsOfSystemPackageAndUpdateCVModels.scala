package baile.dao.mongo.migrations.history

import java.time.{ Instant, LocalDate, Month }

import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.migrations.MongoMigration
import org.bson.types.ObjectId
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonString }

import scala.concurrent.{ ExecutionContext, Future }

object InsertPipelineOperatorsOfSystemPackageAndUpdateCVModels extends MongoMigration(
  "Create dcProjectPackage for system level and insert pipeline operators in it, And modify cvmodels",
  LocalDate.of(2019, Month.MARCH, 22)
) {

  // scalastyle:off method.length
  override def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {

    val packageId = new ObjectId
    val fcn1OperatorId = new ObjectId
    val fcn2OperatorId = new ObjectId
    val fcn3OperatorId = new ObjectId
    val kpcaMnlOperatorId = new ObjectId
    val rfbNetOperatorId = new ObjectId
    val stackedOperatorId = new ObjectId
    val scaeOperatorId = new ObjectId
    val vgg16OperatorId = new ObjectId
    val vgg16RFBOperatorId = new ObjectId
    val squeezeNextReducedOperatorId = new ObjectId
    val squeezeNextOperatorId = new ObjectId
    val rpcaMnlOperatorId = new ObjectId
    val freeScaleOperatorId = new ObjectId

    val systemPackage = Document(
      "_id" -> packageId,
      "name" -> BsonString("deepcortex-ml-lib"),
      "version" -> BsonString("1.0.0"),
      "created" -> BsonString(Instant.now().toString),
      "description" -> BsonString("package for deepcortex pipeline operators")
    )
    val fcn1Document = Document(
      "_id" -> fcn1OperatorId,
      "packageId" -> BsonString(packageId.toString),
      "name" -> BsonString("FCN 1-layer Classifier"),
      "isNeural" -> BsonBoolean(true),
      "moduleName" -> BsonString("ml_lib.classifiers.fcn.fcn1"),
      "className" -> BsonString("FCN1"),
      "operatorType" -> BsonString("CLASSIFIER"),
      "params" -> BsonArray()
    )
    val fcn2Document = Document(
      "_id" -> fcn2OperatorId,
      "packageId" -> BsonString(packageId.toString),
      "name" -> BsonString("FCN 2-layer Classifier"),
      "isNeural" -> BsonBoolean(true),
      "moduleName" -> BsonString("ml_lib.classifiers.fcn.fcn2"),
      "className" -> BsonString("FCN2"),
      "operatorType" -> BsonString("CLASSIFIER"),
      "params" -> BsonArray()
    )
    val fcn3Document = Document(
      "_id" -> fcn3OperatorId,
      "packageId" -> BsonString(packageId.toString),
      "name" -> BsonString("FCN 3-layer Classifier"),
      "isNeural" -> BsonBoolean(true),
      "moduleName" -> BsonString("ml_lib.classifiers.fcn.fcn3"),
      "className" -> BsonString("FCN3"),
      "operatorType" -> BsonString("CLASSIFIER"),
      "params" -> BsonArray()
    )
    val kpcaMnlDocument = Document(
      "_id" -> kpcaMnlOperatorId,
      "packageId" -> BsonString(packageId.toString),
      "name" -> BsonString("KPCA_MNL"),
      "isNeural" -> BsonBoolean(false),
      "moduleName" -> BsonString("ml_lib.classifiers.kpca_mnl.models.kpca_mnl"),
      "className" -> BsonString("KPCA_MNL"),
      "operatorType" -> BsonString("CLASSIFIER"),
      "params" -> BsonArray()
    )
    val rpcaMnlDocument = Document(
      "_id" -> rpcaMnlOperatorId,
      "packageId" -> BsonString(packageId.toString),
      "name" -> BsonString("RPCA_MNL"),
      "isNeural" -> BsonBoolean(false),
      "moduleName" -> BsonString("ml_lib.classifiers.kpca_mnl.models.rpca_mnl"),
      "className" -> BsonString("RPCA_MNL"),
      "operatorType" -> BsonString("CLASSIFIER"),
      "params" -> BsonArray()
    )
    val freeScaleDocument = Document(
      "_id" -> freeScaleOperatorId,
      "packageId" -> BsonString(packageId.toString),
      "name" -> BsonString("Free Scale"),
      "isNeural" -> BsonBoolean(true),
      "moduleName" -> BsonString("ml_lib.classifiers.free_scale.free_scale"),
      "className" -> BsonString("FreeScale"),
      "operatorType" -> BsonString("CLASSIFIER"),
      "params" -> BsonArray()
    )
    val rfbNetDocument = Document(
      "_id" -> rfbNetOperatorId,
      "packageId" -> BsonString(packageId.toString),
      "name" -> BsonString("RFBNet"),
      "isNeural" -> BsonBoolean(true),
      "moduleName" -> BsonString("ml_lib.detectors.rfb_detector.RFBDetector"),
      "className" -> BsonString("RFBDetector"),
      "operatorType" -> BsonString("DETECTOR"),
      "params" -> BsonArray()
    )
    val stackedDocument = Document(
      "_id" -> stackedOperatorId,
      "packageId" -> BsonString(packageId.toString),
      "name" -> BsonString("STACKED"),
      "isNeural" -> BsonBoolean(true),
      "moduleName" -> BsonString("ml_lib.models.autoencoder.scae_model"),
      "className" -> BsonString("SCAEModel"),
      "operatorType" -> BsonString("DECODER"),
      "params" -> BsonArray()
    )
    val scaeDocument = Document(
      "_id" -> scaeOperatorId,
      "packageId" -> BsonString(packageId.toString),
      "name" -> BsonString("Stacked AutoEncoder"),
      "isNeural" -> BsonBoolean(true),
      "moduleName" -> BsonString("ml_lib.feature_extractors.backbones.scae"),
      "className" -> BsonString("StackedAutoEncoder"),
      "operatorType" -> BsonString("UTLP"),
      "params" -> BsonArray()
    )
    val vgg16Document = Document(
      "_id" -> vgg16OperatorId,
      "packageId" -> BsonString(packageId.toString),
      "name" -> BsonString("VGG16"),
      "isNeural" -> BsonBoolean(true),
      "moduleName" -> BsonString("ml_lib.feature_extractors.backbones.vgg16"),
      "className" -> BsonString("VGG16"),
      "operatorType" -> BsonString("UTLP"),
      "params" -> BsonArray()
    )
    val vgg16RFBDocument = Document(
      "_id" -> vgg16RFBOperatorId,
      "packageId" -> BsonString(packageId.toString),
      "name" -> BsonString("VGG16_RFB"),
      "isNeural" -> BsonBoolean(true),
      "moduleName" -> BsonString("ml_lib.feature_extractors.backbones.vgg16_rfb"),
      "className" -> BsonString("VGG16_RFB"),
      "operatorType" -> BsonString("UTLP"),
      "params" -> BsonArray()
    )
    val squeezeNextDocument = Document(
      "_id" -> squeezeNextOperatorId,
      "packageId" -> BsonString(packageId.toString),
      "name" -> BsonString("SQUEEZENEXT"),
      "isNeural" -> BsonBoolean(true),
      "moduleName" -> BsonString("ml_lib.feature_extractors.backbones.squeezenext"),
      "className" -> BsonString("SqueezeNext"),
      "operatorType" -> BsonString("UTLP"),
      "params" -> BsonArray()
    )
    val squeezeNextReducedDocument = Document(
      "_id" -> squeezeNextReducedOperatorId,
      "packageId" -> BsonString(packageId.toString),
      "name" -> BsonString("SQUEEZENEXT_REDUCED"),
      "isNeural" -> BsonBoolean(true),
      "moduleName" -> BsonString("ml_lib.feature_extractors.backbones.squeezenext_reduced"),
      "className" -> BsonString("SqueezeNextReduced"),
      "operatorType" -> BsonString("UTLP"),
      "params" -> BsonArray()
    )
    val operatorDocuments = Seq(
      fcn1Document,
      fcn2Document,
      fcn3Document,
      kpcaMnlDocument,
      squeezeNextDocument,
      rfbNetDocument,
      squeezeNextReducedDocument,
      scaeDocument,
      stackedDocument,
      vgg16Document,
      vgg16RFBDocument,
      rpcaMnlDocument,
      freeScaleDocument
    )

    val dcProjectPackages = db.getCollection("DCProjectPackages")
    val pipelineOperators = db.getCollection("PipelineOperators")
    val cvModels = db.getCollection("CVModels")

    def convertOldModelTypeToNewModelType(oldDocument: Document): Document = {
      val typeField = oldDocument.get[BsonString]("type").get.getValue match {
        case "LOCALIZER" => "LOCALIZER"
        case "CLASSIFIER" => "CLASSIFIER"
        case "AUTO_ENCODER" => "DECODER"
      }
      val newOperatorIdField = oldDocument.get[BsonString]("name").get.getValue match {
        case "KPCA_MNL" => kpcaMnlOperatorId
        case "FCN_1" => fcn1OperatorId
        case "FCN_2" => fcn2OperatorId
        case "FCN_3" => fcn3OperatorId
        case "RFBNet" => rfbNetOperatorId
        case "STACKED" => stackedOperatorId
        case "RPCA_MNL" => rpcaMnlOperatorId
        case "FREESCALE" => freeScaleOperatorId
      }

      Document(
        "type" -> BsonString(typeField),
        "operatorId" -> BsonString(newOperatorIdField.toString)
      )
    }

    def convertOldCVModelToNewCVModel(oldModelDocument: Document): Document = {
      val newFeatureExtractorArchitecture =
        oldModelDocument.getMandatory[BsonString]("featureExtractorArchitecture").getValue match {
          case "SCAE" => scaeOperatorId
          case "VGG16" => vgg16OperatorId
          case "VGG16_RFB" => vgg16RFBOperatorId
          case "SQUEEZENEXT" => squeezeNextOperatorId
          case "SQUEEZENEXT_REDUCED" => squeezeNextReducedOperatorId
        }

      oldModelDocument ++ Document(
        "type" -> convertOldModelTypeToNewModelType(
          oldModelDocument.getChildMandatory("type")
        ),
        "featureExtractorArchitecture" -> BsonString(newFeatureExtractorArchitecture.toString)
      )
    }

    val cvModelsArchitectureAndModelTypeUpdate = for {
      oldModelDocument <- cvModels.find()
      newModelDocument = convertOldCVModelToNewCVModel(oldModelDocument)
      result <- cvModels.replaceOne(
        Document("_id" -> oldModelDocument.getObjectId("_id")),
        newModelDocument
      )
    } yield result

    for {
      _ <- dcProjectPackages.insertOne(systemPackage).toFuture
      _ <- pipelineOperators.insertMany(operatorDocuments).toFuture
      _ <- cvModelsArchitectureAndModelTypeUpdate.toFuture
    } yield ()
  }
  // scalastyle:off method.length

}
