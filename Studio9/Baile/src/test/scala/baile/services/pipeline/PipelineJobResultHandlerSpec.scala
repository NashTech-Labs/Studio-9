package baile.services.pipeline

import java.util.UUID

import akka.actor.Props
import akka.pattern.ask
import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.experiment.ExperimentDao
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.job.CortexJobStatus
import baile.domain.pipeline.PipelineStep
import baile.domain.pipeline.pipeline.GenericExperimentPipeline
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.experiment.{ ExperimentCommonService, ExperimentRandomGenerator }
import baile.services.process.JobResultHandler.{ HandleException, HandleJobResult }
import cortex.api.job.pipeline.OperatorApplicationSummary.Summary
import cortex.api.job.pipeline.PipelineStepResponse.Response
import cortex.api.job.pipeline.{ OperatorApplicationSummary, PipelineRunResponse, PipelineStepResponse, TrackedAssetReference }
import play.api.libs.json.Json

class PipelineJobResultHandlerSpec extends ExtendedBaseSpec {

  trait Setup {
    val experimentDao = mock[ExperimentDao]
    val experimentCommonService = mock[ExperimentCommonService]
    val cortexJobService = mock[CortexJobService]
    val jobMetaService = mock[JobMetaService]

    val handler = system.actorOf(Props(new PipelineJobResultHandler(
      experimentDao = experimentDao,
      experimentCommonService = experimentCommonService,
      cortexJobService = cortexJobService,
      jobMetaService = jobMetaService
    )))
    val experiment = ExperimentRandomGenerator.randomExperiment(
      pipeline = GenericExperimentPipeline(
        steps = Seq(PipelineStep(
          id = randomString(),
          operatorId = randomString(),
          inputs = Map.empty,
          params = Map.empty,
          coordinates = None
        )),
        assets = Seq(AssetReference(
          id = randomString(),
          `type` = AssetType.Experiment
        ))
      )
    )
    val jobId = UUID.randomUUID()
    val outputPath = randomString()
    val labels = Seq("label1", "label2")

    val generalResponse = cortex.api.job.pipeline.PipelineStepGeneralResponse(
      stepId = randomString(),
      trackedAssetReferences = Seq(TrackedAssetReference(
        assetId = randomString(),
        assetType = cortex.api.job.pipeline.AssetType.TabularModel
      )),
      summaries = Seq(OperatorApplicationSummary(
        summary = Summary.ConfusionMatrix(
          value = cortex.api.job.common.ConfusionMatrix(
            confusionMatrixCells = Seq.empty,
            labels = Seq.empty
          )
        )
      ))
    )

    val jobResult = PipelineRunResponse(
      pipelineStepsResponse = Seq(PipelineStepResponse(
        response = Response.PipelineStepGeneralResponse(generalResponse)
      ))
    )
  }

  "PipelineStepResultHandler" should {

    "handle HandleJobResult message for PipelineStepGeneralResponse" in new Setup {
      experimentCommonService.loadExperimentMandatory(experiment.id) shouldReturn future(experiment)
      cortexJobService.getJobOutputPath(jobId) shouldReturn future(outputPath)
      jobMetaService.readRawMeta(jobId, outputPath) shouldReturn future(jobResult.toByteArray)
      experimentDao.update(experiment.id, *)(*) shouldReturn future(Some(experiment))

      whenReady(handler ? HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(PipelineJobResultHandler.Meta(
          experimentId = experiment.id
        ))
      )) (_ shouldBe (()))
    }

    "handle HandleException message" in new Setup {
      experimentDao.update(experiment.id, *)(*) shouldReturn future(Some(experiment))

      whenReady(handler ? HandleException(
        rawMeta = Json.toJsObject(PipelineJobResultHandler.Meta(
          experimentId = experiment.id
        ))
      )) (_ shouldBe (()))
    }
  }

}
