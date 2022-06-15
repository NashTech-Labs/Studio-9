package baile.dao.images

import akka.NotUsed
import akka.stream.alpakka.mongodb.scaladsl.MongoSource
import akka.stream.scaladsl.Source
import baile.dao.asset.Filters.SearchQuery
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.images._
import baile.domain.images.augmentation.MirroringAxisToFlip.{ Both, Horizontal, Vertical }
import baile.domain.images.augmentation.OcclusionMode.{ Background, Zero }
import baile.domain.images.augmentation.TranslationMode.{ Constant, Reflect }
import baile.domain.images.augmentation._
import org.bson.types.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson._
import org.mongodb.scala.model.Aggregates._
import org.mongodb.scala.model.Filters.{ and, in }
import org.mongodb.scala.model.Updates.{ combine, pushEach, set }
import org.mongodb.scala.model.{ BsonField, UnwindOptions, UpdateOneModel, Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

object PictureDao {

  case class AlbumIdIs(albumId: String) extends Filter
  case class AlbumIdIn(albumIds: Seq[String]) extends Filter
  case class FilePathIn(filePaths: Seq[String]) extends Filter
  case class LabelsAre(labels: Seq[String]) extends Filter
  case class PredictedLabelsAre(labels: Seq[String]) extends Filter
  case object HasTags extends Filter
  case object HasPredictedTags extends Filter
  case object HasNoAugmentations extends Filter
  case class AugmentationTypesAre(augmentationTypes: Seq[AugmentationType]) extends Filter

  case object FileName extends Field
  case object FileSize extends Field
  case object Caption extends Field
  case object PredictedCaption extends Field
  case object Labels extends Field
  case object PredictedLabels extends Field

  case class MetaUpdateInfo(
    fileName: String,
    tags: Seq[PictureTag],
    meta: Map[String, String] = Map.empty,
    replaceTags: Boolean
  )
}

class PictureDao(protected val database: MongoDatabase) extends MongoEntityDao[Picture] {

  import baile.dao.images.PictureDao._

  override val collectionName: String = "pictures"

  override protected val fieldMapper: Map[Field, String] = Map(
    FileName -> "fileName",
    FileSize -> "fileSize",
    Caption -> "caption",
    PredictedCaption -> "predictedCaption",
    Labels -> "tags.label",
    PredictedLabels -> "predictedTags.label"
  )

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {

    def hasTagsInField(fieldName: String): Try[Predicate] = Try(MongoFilters.and(
      MongoFilters.exists(fieldName),
      MongoFilters.ne(fieldName, Seq())
    ))

    def labelsInFieldAre(labels: Seq[String], fieldName: String): Try[Predicate] = Try {
      MongoFilters.or(labels.map { label =>
        MongoFilters.elemMatch(fieldName, MongoFilters.eq("label", label))
      }: _*)
    }

    {
      case AlbumIdIs(albumId) => Try(MongoFilters.equal("albumId", albumId))
      case AlbumIdIn(albumIds) => Try(MongoFilters.in("albumId", albumIds: _*))
      case FilePathIn(filePaths) => Try(MongoFilters.in("filePath", filePaths: _*))
      case HasTags => hasTagsInField("tags")
      case LabelsAre(labels) => labelsInFieldAre(labels, "tags")
      case HasPredictedTags => hasTagsInField("predictedTags")
      case PredictedLabelsAre(labels) => labelsInFieldAre(labels, "predictedTags")
      // TODO add protection from injections to regex
      case SearchQuery(term) => Try(MongoFilters.regex("fileName", term, "i"))
      case AugmentationTypesAre(augmentationTypes) => Try {
        MongoFilters.or(augmentationTypes.map { augmentationType =>
          MongoFilters.elemMatch(
            "appliedAugmentations",
            MongoFilters.eq(
              "generalParams.augmentationType",
              AugmentationSerializers.augmentationTypeToString(augmentationType)
            )
          )
        }: _*)
      }
      case HasNoAugmentations => Try(MongoFilters.or(
        MongoFilters.exists("appliedAugmentations", false),
        MongoFilters.eq("appliedAugmentations", Seq())
      ))
    }
  }

  override protected[images] def entityToDocument(entity: Picture): Document = Document(
    "albumId" -> entity.albumId,
    "filePath" -> entity.filePath,
    "fileName" -> entity.fileName,
    "fileSize" -> entity.fileSize,
    "caption" -> entity.caption,
    "predictedCaption" -> entity.predictedCaption,
    "tags" -> entity.tags.map(entity => pictureTagToDocument(entity)),
    "predictedTags" -> entity.predictedTags.map(entity => pictureTagToDocument(entity)),
    "meta" -> BsonDocument(entity.meta.mapValues(BsonString(_))),
    "originalPictureId" -> entity.originalPictureId.map(BsonString(_)),
    "appliedAugmentations" -> entity.appliedAugmentations.map {
      _.map(AugmentationSerializers.appliedAugmentationToDocument)
    }
  )

  override protected[images] def documentToEntity(document: Document): Try[Picture] = Try {
    Picture(
      albumId = document.getMandatory[BsonString]("albumId").getValue,
      filePath = document.getMandatory[BsonString]("filePath").getValue,
      fileName = document.getMandatory[BsonString]("fileName").getValue,
      fileSize = document.get[BsonInt64]("fileSize").map(value => value.getValue),
      caption = document.get[BsonString]("caption").map(value => value.getValue),
      predictedCaption = document.get[BsonString]("predictedCaption").map(value => value.getValue),
      tags = document.getMandatory[BsonArray]("tags").map(bsonValue => documentToPictureTag(bsonValue.asDocument)),
      predictedTags = document.getMandatory[BsonArray]("predictedTags").map { bsonValue =>
        documentToPictureTag(bsonValue.asDocument)
      },
      meta = document.getChild("meta").map { doc =>
        doc.keys.map(key => (key, doc.getMandatory[BsonString](key).getValue)).toMap
      }.getOrElse(Map.empty),
      originalPictureId = document.get[BsonString]("originalPictureId").map(_.getValue),
      appliedAugmentations = document.get[BsonArray]("appliedAugmentations").map(_.map {
        bsonValue => AugmentationSerializers.documentToAppliedAugmentation(bsonValue.asDocument())
      })
    )
  }

  def updateMeta(
    albumId: String,
    meta: Seq[MetaUpdateInfo]
  )(implicit ec: ExecutionContext): Future[Int] = {

    def getPicturesFileToIdMap: Future[Map[String, ObjectId]] = {
      val query = List(
        filter(and(
          BsonDocument("albumId" -> albumId),
          in("fileName", meta.map(_.fileName): _*)
        )),
        project(BsonDocument(
          "mongoId" -> "$_id",
          "_id" -> false,
          "fileName" -> true
        ))
      )

      collection.aggregate(query).toFuture
        .map(_.map(document =>
          document.getString("fileName") -> document.getObjectId("mongoId")
        ).toMap)
    }

    def prepareUpdateQueries(fileToIdMap: Map[String, ObjectId]): Seq[UpdateOneModel[Nothing]] =
      meta.collect {
        case item if fileToIdMap.contains(item.fileName) =>
          val pictureId = fileToIdMap(item.fileName)
          val tagsUpdate =
            if (item.replaceTags) set("tags", item.tags.map(pictureTagToDocument))
            else pushEach("tags", item.tags.map(pictureTagToDocument): _*)
          val metaUpdate =
            combine(
              item.meta.map {
                case (key, value) => set(s"meta.$key", BsonString(value))
              }.toSeq: _*
            )
          UpdateOneModel(Document("_id" -> pictureId), combine(
            tagsUpdate,
            metaUpdate
          ))
      }

    def updatePictures(entityUpdates: Seq[UpdateOneModel[Nothing]]): Future[Int] =
      if (entityUpdates.isEmpty) Future.successful(0)
      else collection.bulkWrite(entityUpdates).toFuture.map(_.getModifiedCount)

    for {
      fileToIdMap <- getPicturesFileToIdMap
      updates = prepareUpdateQueries(fileToIdMap)
      count <- updatePictures(updates)
    } yield count
  }

  def getLabelsStats(albumId: String)(implicit ec: ExecutionContext): Future[Map[String, Int]] = {
    val query = List(filter(BsonDocument("albumId" -> albumId)))
    fetchLabelStats(albumId, "tags", query)
  }

  def getPredictedLabelsStats(albumId: String)(implicit ec: ExecutionContext): Future[Map[String, Int]] = {
    val query = List(filter(BsonDocument("albumId" -> albumId)))
    fetchLabelStats(albumId, "predictedTags", query)
  }

  def getAllLabelsStats(albumId: String)(implicit ec: ExecutionContext): Future[Map[String, Int]] = {
    val query = List(
      filter(BsonDocument("albumId" -> albumId)),
      project(BsonDocument(
        "allValues" -> BsonDocument("$setUnion" -> BsonArray("$tags", "$predictedTags"))
      )))
    fetchLabelStats(albumId, "allValues", query)
  }

  def exportTags(albumId: String)(implicit ec: ExecutionContext): Source[PictureTagsSummary, NotUsed] = {
    def documentToSummary(document: Document): Try[PictureTagsSummary] = Try {
      PictureTagsSummary(
        fileName = document.getMandatory[BsonString]("fileName").getValue,
        tags = document.getMandatory[BsonArray]("tags").asScala
          .map(tagValue => documentToPictureTag(tagValue.asDocument)),
        predictedTags = document.getMandatory[BsonArray]("predictedTags").asScala
          .map(tagValue => documentToPictureTag(tagValue.asDocument))
      )
    }

    val query = List(
      filter(BsonDocument("albumId" -> albumId)),
      project(BsonDocument(
        "fileName" -> true,
        "tags" -> true,
        "predictedTags" -> true
      ))
    )

    MongoSource(
      collection.aggregate(query).map(documentToSummary(_).get)
    )
  }

  private def pictureTagAreaToDocument(entity: PictureTagArea): Document =
    Document(
      "top" -> entity.top,
      "left" -> entity.left,
      "height" -> entity.height,
      "width" -> entity.width
    )

  private def pictureTagToDocument(entity: PictureTag): Document =
    Document(
      "label" -> entity.label,
      "area" -> entity.area.map(pictureTagArea => Some(pictureTagAreaToDocument(pictureTagArea))),
      "confidence" -> entity.confidence
    )

  private def documentToPictureTagArea(document: Document): PictureTagArea =
    PictureTagArea(
      top = document.getMandatory[BsonInt32]("top").getValue,
      left = document.getMandatory[BsonInt32]("left").getValue,
      height = document.getMandatory[BsonInt32]("height").getValue,
      width = document.getMandatory[BsonInt32]("width").getValue
    )

  private def documentToPictureTag(document: Document): PictureTag =
    PictureTag(
      label = document.getMandatory[BsonString]("label").getValue,
      area = document.get[BsonDocument]("area").map(value => documentToPictureTagArea(value)),
      confidence = document.get[BsonDouble]("confidence").map(value => value.getValue)
    )

  private def fetchLabelStats(
    albumId: String,
    field: String,
    query: List[Bson]
  )(implicit ec: ExecutionContext): Future[Map[String, Int]] = {

    val completeQuery = query ++ List(
      unwind(s"$$$field", UnwindOptions().preserveNullAndEmptyArrays(true)),
      group(s"$$$field.label", BsonField("count", BsonDocument("$sum" -> 1))),
      project(BsonDocument(
        "label" -> "$_id",
        "_id" -> false,
        "count" -> true
      )),
      sort(BsonDocument("count" -> -1))
    )

    collection.aggregate(completeQuery).toFuture
      .map(_.map(document =>
        document.getString("label") -> document.getInteger("count").toInt
      ).toMap)
  }

}

private object AugmentationSerializers {

  def documentToAppliedAugmentation(document: Document): AppliedAugmentation = {
    val generalParamsDocument = document.getChildMandatory("generalParams")
    val generalParams =
      stringToAugmentationType(generalParamsDocument.getMandatory[BsonString]("augmentationType").getValue) match {
        case AugmentationType.Rotation => documentToAppliedAugmentationRotation(generalParamsDocument)
        case AugmentationType.Translation => documentToAppliedAugmentationTranslation(generalParamsDocument)
        case AugmentationType.Noising => documentToAppliedAugmentationNoising(generalParamsDocument)
        case AugmentationType.Shearing => documentToAppliedAugmentationShearing(generalParamsDocument)
        case AugmentationType.ZoomIn => documentToAppliedAugmentationZoomIn(generalParamsDocument)
        case AugmentationType.ZoomOut => documentToAppliedAugmentationZoomOut(generalParamsDocument)
        case AugmentationType.Occlusion => documentToAppliedAugmentationOcclusion(generalParamsDocument)
        case AugmentationType.SaltPepper => documentToAppliedAugmentationSaltPepper(generalParamsDocument)
        case AugmentationType.Mirroring => documentToAppliedAugmentationMirroring(generalParamsDocument)
        case AugmentationType.Cropping => documentToAppliedAugmentationCropping(generalParamsDocument)
        case AugmentationType.Blurring => documentToAppliedAugmentationBlurring(generalParamsDocument)
        case AugmentationType.PhotoDistort => documentToAppliedAugmentationPhotometricDistort(generalParamsDocument)
      }
    AppliedAugmentation(
      generalParams = generalParams,
      extraParams = documentToParams(document.getChildMandatory("extraParams")),
      internalParams = documentToParams(document.getChildMandatory("internalParams"))
    )
  }

  def documentToAppliedAugmentationRotation(document: Document): AppliedRotationParams =
    AppliedRotationParams(
      angle = document.getMandatory[BsonDouble]("angle").getValue.toFloat,
      resize = document.getMandatory[BsonBoolean]("resize").getValue
    )

  def documentToAppliedAugmentationTranslation(document: Document): AppliedTranslationParams =
    AppliedTranslationParams(
      translateFraction = document.getMandatory[BsonNumber]("translateFraction").doubleValue.toFloat,
      mode = document.getMandatory[BsonString]("mode").getValue match {
        case "Reflect" => TranslationMode.Reflect
        case "Constant" => TranslationMode.Constant
      },
      resize = document.getMandatory[BsonBoolean]("resize").getValue
    )

  def documentToAppliedAugmentationShearing(document: Document): AppliedShearingParams =
    AppliedShearingParams(
      angle = document.getMandatory[BsonNumber]("angle").doubleValue.toFloat,
      resize = document.getMandatory[BsonBoolean]("resize").getValue
    )

  def documentToAppliedAugmentationNoising(document: Document): AppliedNoisingParams = AppliedNoisingParams(
    noiseSignalRatio = document.getMandatory[BsonNumber]("noiseSignalRatio").doubleValue.toFloat
  )

  def documentToAppliedAugmentationZoomIn(document: Document): AppliedZoomInParams = AppliedZoomInParams(
    magnification = document.getMandatory[BsonNumber]("magnification").doubleValue.toFloat,
    resize = document.getMandatory[BsonBoolean]("resize").getValue
  )

  def documentToAppliedAugmentationZoomOut(document: Document): AppliedZoomOutParams = AppliedZoomOutParams(
    shrinkFactor = document.getMandatory[BsonNumber]("shrinkFactor").doubleValue.toFloat,
    resize = document.getMandatory[BsonBoolean]("resize").getValue
  )

  def documentToAppliedAugmentationOcclusion(document: Document): AppliedOcclusionParams =
    AppliedOcclusionParams(
      occAreaFraction = document.getMandatory[BsonNumber]("occAreaFraction").doubleValue.toFloat,
      mode = document.getMandatory[BsonString]("mode").getValue match {
        case "Background" => OcclusionMode.Background
        case "Zero" => OcclusionMode.Zero
      },
      isSarAlbum = document.getMandatory[BsonBoolean]("isSarAlbum").getValue,
      tarWinSize = document.getMandatory[BsonInt32]("tarWinSize").getValue
    )

  def documentToAppliedAugmentationSaltPepper(document: Document): AppliedSaltPepperParams =
    AppliedSaltPepperParams(
      knockoutFraction = document.getMandatory[BsonNumber]("knockoutFraction").doubleValue.toFloat,
      pepperProbability = document.getMandatory[BsonNumber]("pepperProbability").doubleValue.toFloat
    )

  def documentToAppliedAugmentationMirroring(document: Document): AppliedMirroringParams =
    AppliedMirroringParams(
      axisFlipped = document.getMandatory[BsonString]("axisFlipped").getValue match {
        case "Horizontal" => MirroringAxisToFlip.Horizontal
        case "Vertical" => MirroringAxisToFlip.Vertical
        case "Both" => MirroringAxisToFlip.Both
      }
    )

  def documentToAppliedAugmentationCropping(document: Document): AppliedCroppingParams =
    AppliedCroppingParams(
      cropAreaFraction = document.getMandatory[BsonNumber]("cropAreaFraction").doubleValue.toFloat,
      resize = document.getMandatory[BsonBoolean]("resize").getValue
    )

  def documentToAppliedAugmentationBlurring(document: Document): AppliedBlurringParams =
    AppliedBlurringParams(
      sigma = document.getMandatory[BsonNumber]("sigma").doubleValue.toFloat
    )

  def documentToAppliedAugmentationPhotometricDistort(document: Document): AppliedPhotometricDistortParams =
    AppliedPhotometricDistortParams(
      alphaContrast = document.getMandatory[BsonNumber]("alphaContrast").doubleValue.toFloat,
      deltaMax = document.getMandatory[BsonNumber]("deltaMax").doubleValue.toFloat,
      alphaSaturation = document.getMandatory[BsonNumber]("alphaSaturation").doubleValue.toFloat,
      deltaHue = document.getMandatory[BsonNumber]("deltaHue").doubleValue.toFloat
  )

  def appliedAugmentationToDocument(appliedAugmentation: AppliedAugmentation): Document = {
    val augmentationType = "augmentationType" -> BsonString(augmentationTypeToString(
      appliedAugmentation.generalParams.augmentationType
    ))
    val generalParams = (appliedAugmentation.generalParams match {
      case AppliedRotationParams(angle, resize) =>
        Document(
          "angle" -> BsonNumber(angle),
          "resize" -> BsonBoolean(resize)
        )
      case AppliedShearingParams(angle, resize) => Document(
        "angle" -> BsonNumber(angle),
        "resize" -> BsonBoolean(resize)
      )
      case AppliedNoisingParams(noiseSignalRatio) => Document(
        "noiseSignalRatio" -> BsonNumber(noiseSignalRatio)
      )
      case AppliedZoomInParams(magnification, resize) => Document(
        "magnification" -> BsonNumber(magnification),
        "resize" -> BsonBoolean(resize)
      )
      case AppliedZoomOutParams(shrinkFactor, resize) => Document(
        "shrinkFactor" -> BsonNumber(shrinkFactor),
        "resize" -> BsonBoolean(resize)
      )
      case occlusion: AppliedOcclusionParams => Document(
        "occAreaFraction" -> BsonNumber(occlusion.occAreaFraction),
        "mode" -> BsonString(occlusion.mode match {
          case Background => "Background"
          case Zero => "Zero"
        }),
        "isSarAlbum" -> BsonBoolean(occlusion.isSarAlbum),
        "tarWinSize" -> BsonInt32(occlusion.tarWinSize)
      )
      case AppliedTranslationParams(translateFraction, mode, resize) => Document(
        "translateFraction" -> BsonNumber(translateFraction),
        "mode" -> BsonString(mode match {
          case Reflect => "Reflect"
          case Constant => "Constant"
        }),
        "resize" -> BsonBoolean(resize)
      )
      case AppliedSaltPepperParams(knockoutFraction, pepperProbability) => Document(
        "knockoutFraction" -> BsonNumber(knockoutFraction),
        "pepperProbability" -> BsonNumber(pepperProbability)
      )
      case AppliedMirroringParams(axisFlipped) => Document(
        "axisFlipped" -> BsonString(axisFlipped match {
          case Horizontal => "Horizontal"
          case Vertical => "Vertical"
          case Both => "Both"
        })
      )
      case AppliedCroppingParams(cropAreaFraction, resize) => Document(
        "cropAreaFraction" -> BsonNumber(cropAreaFraction),
        "resize" -> BsonBoolean(resize)
      )
      case AppliedBlurringParams(sigma) => Document(
        "sigma" -> BsonNumber(sigma)
      )
      case AppliedPhotometricDistortParams(alphaConstant, deltaMax, alphaSaturation, deltaHue) => Document(
        "alphaContrast" -> BsonNumber(alphaConstant),
        "deltaMax" -> BsonNumber(deltaMax),
        "alphaSaturation" -> BsonNumber(alphaSaturation),
        "deltaHue" -> BsonNumber(deltaHue)
      )
    }) + augmentationType

    Document(
      "generalParams" -> generalParams,
      "extraParams" -> paramsToDocument(appliedAugmentation.extraParams),
      "internalParams" -> paramsToDocument(appliedAugmentation.internalParams)
    )
  }

  def paramsToDocument(params: Map[String, Float]): Document = Document(params.mapValues(BsonDouble(_)))

  def documentToParams(document: Document): Map[String, Float] =
    document.toMap.mapValues {
      case d: BsonDouble => d.getValue.toFloat
      case unknown => throw new RuntimeException(
        s"Unexpected DA applied param value $unknown. Type ${ unknown.getClass }"
      )
    }

  def augmentationTypeToString(augmentationType: AugmentationType): String = augmentationType match {
    case AugmentationType.Rotation => "rotation"
    case AugmentationType.Shearing => "shearing"
    case AugmentationType.Noising => "noising"
    case AugmentationType.ZoomIn => "zoomIn"
    case AugmentationType.ZoomOut => "zoomOut"
    case AugmentationType.Occlusion => "occlusion"
    case AugmentationType.Translation => "translation"
    case AugmentationType.SaltPepper => "saltPepper"
    case AugmentationType.Mirroring => "mirroring"
    case AugmentationType.Cropping => "cropping"
    case AugmentationType.Blurring => "blurring"
    case AugmentationType.PhotoDistort => "photometricDistort"
  }

  def stringToAugmentationType(augmentationType: String): AugmentationType = augmentationType match {
    case "rotation" => AugmentationType.Rotation
    case "shearing" => AugmentationType.Shearing
    case "noising" => AugmentationType.Noising
    case "zoomIn" => AugmentationType.ZoomIn
    case "zoomOut" => AugmentationType.ZoomOut
    case "occlusion" => AugmentationType.Occlusion
    case "translation" => AugmentationType.Translation
    case "saltPepper" => AugmentationType.SaltPepper
    case "mirroring" => AugmentationType.Mirroring
    case "cropping" => AugmentationType.Cropping
    case "blurring" => AugmentationType.Blurring
    case "photometricDistort" => AugmentationType.PhotoDistort
  }

}
