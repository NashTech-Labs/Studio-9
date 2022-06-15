package baile.services.cv.model.util.export.format.v1

import baile.services.cv.model.util.export
import play.api.libs.json.{ Json, OFormat }

private[export] case class CVFeatureExtractorExportMeta(
  name: String,
  architecture: CVFeatureExtractorArchitecture,
  consumer: Option[CVModelType],
  summary: Option[CVModelSummary]
) {

  def toDomain: Either[String, export.CVModelExportMeta] = {
    def buildTlConsumer: Either[String, export.CVModelExportMeta.CVModelType.TLConsumer] =
      consumer
        .map(_.toTlConsumer)
        .orElse {
          architecture match {
            case CVFeatureExtractorArchitecture.StackedAutoEncoder =>
              Some(
                export.CVModelExportMeta.CVModelType.TLConsumer.Decoder(
                  export.CVModelExportMeta.ClassReference(
                    moduleName = "ml_lib.models.autoencoder.scae_model",
                    className = "SCAEModel",
                    packageName = "deepcortex-ml-lib",
                    packageVersion = None
                  )
                )
              )

            case _ => None
          }
        }
        .toRight("FE consumer not specified")

    for {
      tlConsumer <- buildTlConsumer
    } yield export.CVModelExportMeta(
      name = Some(name),
      modelType = export.CVModelExportMeta.CVModelType.TL(
        consumer = tlConsumer,
        featureExtractorReference = architecture.toClassReference,
        featureExtractorOnly = true
      ),
      description = None,
      classNames = summary.map(_.labels)
    )
  }

}

private[export] object CVFeatureExtractorExportMeta {

  implicit val CVFeatureExtractorExportMetaFormat: OFormat[CVFeatureExtractorExportMeta] =
    Json.format[CVFeatureExtractorExportMeta]

}
