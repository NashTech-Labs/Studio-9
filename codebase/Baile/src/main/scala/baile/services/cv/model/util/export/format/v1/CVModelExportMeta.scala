package baile.services.cv.model.util.export.format.v1

import baile.services.cv.model.util.export
import baile.utils.json.EnumFormatBuilder
import cats.implicits._
import play.api.libs.json._

private[export] case class CVModelExportMeta(
  name: String,
  modelType: CVModelType,
  featureExtractorArchitecture: Option[CVFeatureExtractorArchitecture],
  localizationMode: Option[CVModelLocalizationMode],
  summary: Option[CVModelSummary],
  testSummary: Option[CVModelSummary],
  description: Option[String]
) {

  def toDomain: Either[String, export.CVModelExportMeta] = {
    for {
      feReference <- Either.fromOption(
        featureExtractorArchitecture,
        "FE architecture not specified"
      ).map(_.toClassReference)
    } yield export.CVModelExportMeta(
      name = Some(name),
      modelType = export.CVModelExportMeta.CVModelType.TL(
        consumer = modelType.toTlConsumer,
        featureExtractorReference = feReference
      ),
      description = description,
      classNames = summary.map(_.labels)
    )
  }

}

private[export] object CVModelExportMeta {

  implicit val CVModelLocalizationModeFormat: Format[CVModelLocalizationMode] = EnumFormatBuilder.build(
    {
      case "TAGS" => CVModelLocalizationMode.Tags
      case "CAPTIONS" => CVModelLocalizationMode.Captions
    },
    {
      case CVModelLocalizationMode.Tags => "TAGS"
      case CVModelLocalizationMode.Captions => "CAPTIONS"
    },
    "cv model pipeline type"
  )

  implicit val CVModelExportJsonFormat: OFormat[CVModelExportMeta] = Json.format[CVModelExportMeta]

}
