package baile.services.cv.model

import java.time.Instant
import java.util.UUID

import baile.RandomGenerators.{ randomOf, randomString }
import baile.daocommons.WithId
import baile.domain.common.CortexModelReference
import baile.domain.cv.model._

object CVModelRandomGenerator {

  // scalastyle:off parameter.number
  def randomModel(
    status: CVModelStatus = randomOf(CVModelStatus.Active, CVModelStatus.Error),
    ownerId: UUID = UUID.randomUUID(),
    modelType: CVModelType = randomTLModelType(),
    featureExtractorReference: Option[CortexModelReference] = randomOf(None, Some(CortexModelReference(
      randomString(),
      randomString()
    ))),
    cortexModelReference: Option[CortexModelReference] = randomOf(None, Some(CortexModelReference(
      randomString(),
      randomString()
    ))),
    name: String = randomString(),
    description: Option[String] = randomOf(None, Some(randomString())),
    featureExtractorId: Option[String] = randomOf(None, Some(randomString())),
    inLibrary: Boolean = false,
    experimentId: Option[String] = None,
    id: String = randomString()
  ): WithId[CVModel] = WithId(
    CVModel(
      ownerId = ownerId,
      name = name,
      created = Instant.now(),
      updated = Instant.now(),
      status = status,
      cortexFeatureExtractorReference = featureExtractorReference,
      cortexModelReference = cortexModelReference,
      `type` = modelType,
      classNames = Some(Seq("foo")),
      featureExtractorId = featureExtractorId,
      description = description,
      inLibrary = inLibrary,
      experimentId = experimentId
    ),
    id
  )
  // scalastyle:on parameter.number

  def randomTLModelType(
    featureExtractorArchitecture: String = "SCAE"
  ): CVModelType.TL =
    CVModelType.TL(
      consumer = randomOf(
        CVModelType.TLConsumer.Localizer("RFBNet"),
        CVModelType.TLConsumer.Classifier("FCN_1")
      ),
      featureExtractorArchitecture = featureExtractorArchitecture
    )

}
