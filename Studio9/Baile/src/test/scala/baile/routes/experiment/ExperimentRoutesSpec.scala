package baile.routes.experiment

import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import baile.daocommons.WithId
import baile.domain.common.ConfusionMatrixCell
import baile.domain.pipeline.PipelineParams._
import baile.domain.cv.model.{ CVModelSummary, CVModelType }
import baile.domain.cv.pipeline.FeatureExtractorParams.CreateNewFeatureExtractorParams
import baile.domain.cv.pipeline.{ CVTLTrainPipeline, CVTLTrainStep1Params }
import baile.domain.cv.result.{ CVTLTrainResult, CVTLTrainStepResult }
import baile.domain.experiment.{ Experiment, ExperimentStatus }
import baile.routes.ExtendedRoutesSpec
import baile.services.experiment.ExperimentService
import baile.services.experiment.ExperimentService.ExperimentServiceError
import cats.implicits._
import play.api.libs.json.{ JsObject, JsString, Json }
import baile.RandomGenerators._
import baile.services.cv.CVTLModelPrimitiveService.CVTLModelPrimitiveServiceError
import baile.services.cv.model.CVModelTrainPipelineHandler.CVModelCreateError
import org.scalatest.prop.TableDrivenPropertyChecks

class ExperimentRoutesSpec extends ExtendedRoutesSpec with TableDrivenPropertyChecks {

  trait Setup extends RoutesSetup { self =>

    val experimentService = mock[ExperimentService]
    val routes = new ExperimentRoutes(
      conf,
      authenticationService,
      experimentService
    ).routes

    val cvPipelineParamsKey = randomString()
    val cvPipelineParamsValue = randomString()
    val classifierId = randomString()

    val cvtlTrainStepOneParams = CVTLTrainStep1Params(
      feParams = CreateNewFeatureExtractorParams(
        featureExtractorArchitecture = randomString(),
        pipelineParams = Map.empty
      ),
      modelType = CVModelType.TLConsumer.Classifier(classifierId),
      modelParams = Map(cvPipelineParamsKey -> StringParam(value = cvPipelineParamsValue)),
      inputAlbumId = randomString(),
      testInputAlbumId = None,
      automatedAugmentationParams = None,
      trainParams = None
    )

    val cvModelSummary = CVModelSummary(
      labels = Seq(randomString()),
      confusionMatrix = Some(Seq(
        ConfusionMatrixCell(
          actualLabel = Some(randomInt(999)),
          predictedLabel = Some(randomInt(999)),
          count = randomInt(999)
        )
      )),
      mAP = Some(randomInt(999)),
      reconstructionLoss = Some(139)
    )

    val cvtlTrainStepResult = CVTLTrainStepResult(
      modelId = randomString(),
      outputAlbumId = Some(randomString()),
      testOutputAlbumId = Some(randomString()),
      autoAugmentationSampleAlbumId = Some(randomString()),
      summary = Some(cvModelSummary),
      testSummary = Some(cvModelSummary),
      augmentationSummary = None,
      trainTimeSpentSummary = None,
      evaluateTimeSpentSummary = None,
      probabilityPredictionTableId = None,
      testProbabilityPredictionTableId = None
    )

    val cvtlTrainResult = CVTLTrainResult(
      stepOne = cvtlTrainStepResult,
      stepTwo = Some(cvtlTrainStepResult)
    )

    val experimentWithId = WithId(
      Experiment(
        name = randomString(),
        ownerId = UUID.randomUUID(),
        description = Some(randomString()),
        status = randomOf(
          ExperimentStatus.Running,
          ExperimentStatus.Completed,
          ExperimentStatus.Error,
          ExperimentStatus.Cancelled
        ),
        pipeline = CVTLTrainPipeline(
          stepOne = cvtlTrainStepOneParams,
          stepTwo = None
        ),
        result = Some(cvtlTrainResult),
        created = Instant.now(),
        updated = Instant.now()
      ),
      "experimentId"
    )

    def validateExperimentResponse(experimentWithId: WithId[Experiment], response: JsObject) = {
      response.fields should contain allOf(
        "id" -> JsString(experimentWithId.id),
        "ownerId" -> JsString(experimentWithId.entity.ownerId.toString),
        "name" -> JsString(experimentWithId.entity.name)
      )
      Instant.parse((response \ "created").as[String]) shouldBe experimentWithId.entity.created
      Instant.parse((response \ "updated").as[String]) shouldBe experimentWithId.entity.updated
      (response \ "result" \ "step1" \ "summary" \ "reconstructionLoss").as[Double] shouldBe 139
      (response \ "result" \ "step2" \ "summary" \ "reconstructionLoss").as[Double] shouldBe 139
    }
  }

  "POST /experiments" should {
    "return success response" in new Setup {
      val cvtlTrainStepOneParamsData = JsObject(Map(
        "featureExtractorModelId" -> JsString(randomString()),
        "modelType" -> JsObject(Map(
          "tlType" -> JsString("CLASSIFICATION"),
          "classifierType" -> JsString(classifierId)
        )),
        "params" -> JsObject(Map(cvPipelineParamsKey -> JsString(cvPipelineParamsValue))),
        "input" -> JsString(cvtlTrainStepOneParams.inputAlbumId)
      ))

      val pipelineData = JsObject(Map(
        "step1" -> cvtlTrainStepOneParamsData
      ))
      val experimentCreateRequest = JsObject(Map(
        "name" -> JsString(experimentWithId.entity.name),
        "description" -> JsString(experimentWithId.entity.description.get),
        "type" -> JsString("CVTLTrain"),
        "pipeline" -> pipelineData
      ))

      experimentService.create(
        Some(experimentWithId.entity.name),
        experimentWithId.entity.description,
        *
      ) shouldReturn future(experimentWithId.asRight)
      Post("/experiments", experimentCreateRequest).signed.check {
        status shouldBe StatusCodes.OK
        validateExperimentResponse(experimentWithId, responseAs[JsObject])
      }
    }

    "return error response" in new Setup {
      val cvtlTrainStepOneParamsData = JsObject(Map(
        "featureExtractorModelId" -> JsString(randomString()),
        "modelType" -> JsObject(Map(
          "tlType" -> JsString("CLASSIFICATION"),
          "classifierType" -> JsString(classifierId)
        )),
        "params" -> JsObject(Map(cvPipelineParamsKey -> JsString(cvPipelineParamsValue))),
        "input" -> JsString(cvtlTrainStepOneParams.inputAlbumId)
      ))

      val pipelineData = JsObject(Map(
        "step1" -> cvtlTrainStepOneParamsData
      ))
      val experimentCreateRequest = JsObject(Map(
        "name" -> JsString(experimentWithId.entity.name),
        "description" -> JsString(experimentWithId.entity.description.get),
        "type" -> JsString("CVTLTrain"),
        "pipeline" -> pipelineData
      ))

      val errors = Table(
        ("error", "expectedStatus"),
        (ExperimentServiceError.ExperimentNotFound, StatusCodes.NotFound),
        (ExperimentServiceError.AccessDenied, StatusCodes.Forbidden),
        (ExperimentServiceError.SortingFieldUnknown, StatusCodes.BadRequest),
        (ExperimentServiceError.NameNotSpecified, StatusCodes.BadRequest),
        (ExperimentServiceError.EmptyExperimentName, StatusCodes.BadRequest),
        (ExperimentServiceError.NameIsTaken, StatusCodes.Conflict),
        (ExperimentServiceError.ExperimentInUse, StatusCodes.BadRequest),

        (
          ExperimentServiceError.ExperimentError(
            CVModelCreateError.CVTLModelPrimitiveError(CVTLModelPrimitiveServiceError.AccessDenied("primitive-id"))
          ),
          StatusCodes.BadRequest
        ),
        (
          ExperimentServiceError.ExperimentError(
            CVModelCreateError.CVTLModelPrimitiveError(CVTLModelPrimitiveServiceError.NotFound("primitive-id"))
          ),
          StatusCodes.BadRequest
        )
      )

      forAll(errors) { (error, expectedStatus) =>
        experimentService.create(
          Some(experimentWithId.entity.name),
          experimentWithId.entity.description,
          *
        ) shouldReturn future(error.asLeft)
        Post("/experiments", experimentCreateRequest).signed.check {
          status shouldBe expectedStatus
          responseAs[JsObject].keys should contain allOf("code", "message")
        }
      }
    }
  }

  "GET /experiments/:id endpoint" should {

    "get experiment" in new Setup {
      experimentService.get("experimentId", *) shouldReturn future(experimentWithId.asRight)
      Get("/experiments/experimentId").signed.check {
        status shouldBe StatusCodes.OK
        validateExperimentResponse(experimentWithId, responseAs[JsObject])
      }
    }

    "not be able to get experiment if it is not found" in new Setup {
      experimentService.get(
        "experimentId",
        *
      ) shouldReturn future(ExperimentServiceError.ExperimentNotFound.asLeft)
      Get("/experiments/experimentId").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "PUT /experiments/:id" should {
    "return success response" in new Setup {
      val experimentUpdateRequest = JsObject(Map(
        "name" -> JsString(experimentWithId.entity.name),
        "description" -> JsString(experimentWithId.entity.description.get)
      ))

      experimentService.update(
        experimentWithId.id,
        Some(experimentWithId.entity.name),
        experimentWithId.entity.description
      ) shouldReturn future(experimentWithId.asRight)
      Put("/experiments/experimentId", experimentUpdateRequest).signed.check {
        status shouldBe StatusCodes.OK
        (responseAs[JsObject] \ "name").as[String] shouldBe experimentWithId.entity.name
        (responseAs[JsObject] \ "description").as[String] shouldBe experimentWithId.entity.description.get
      }
    }

    "return error response" in new Setup {
      val experimentUpdateRequest = JsObject(Map(
        "name" -> JsString(experimentWithId.entity.name),
        "description" -> JsString(experimentWithId.entity.description.get)
      ))

      experimentService.update(
        experimentWithId.id,
        Some(experimentWithId.entity.name),
        experimentWithId.entity.description
      ) shouldReturn future(ExperimentServiceError.NameIsTaken.asLeft)
      Put("/experiments/experimentId", experimentUpdateRequest).signed.check {
        status shouldBe StatusCodes.Conflict
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "DELETE /experiments/:id" should {
    "return success response" in new Setup {
      experimentService.delete(experimentWithId.id) shouldReturn future(().asRight)
      Delete("/experiments/experimentId").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe Json.parse(s"""{"id": "experimentId"}""")
      }
    }

    "return error response" in new Setup {
      experimentService.delete(
        experimentWithId.id
      ) shouldReturn future(ExperimentServiceError.ExperimentNotFound.asLeft)
      Delete("/experiments/experimentId").signed.check {
        status shouldBe StatusCodes.NotFound
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

}
