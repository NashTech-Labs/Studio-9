package baile.services.experiment

import java.time.Instant
import java.util.UUID

import baile.RandomGenerators.{ randomOpt, randomString }
import baile.daocommons.WithId
import baile.domain.experiment.pipeline.ExperimentPipeline
import baile.domain.experiment.result.ExperimentResult
import baile.domain.experiment.{ Experiment, ExperimentStatus }

object ExperimentRandomGenerator {

  def randomExperiment(
    pipeline: ExperimentPipeline,
    result: Option[ExperimentResult] = None,
    id: String = randomString(),
    name: String = randomString(),
    ownerId: UUID = UUID.randomUUID(),
    description: Option[String] = randomOpt(randomString()),
    status: ExperimentStatus = ExperimentStatus.Running,
    created: Instant = Instant.now(),
    updated: Instant = Instant.now()
  ): WithId[Experiment] = {
    WithId(
      Experiment(
        name = name,
        ownerId = ownerId,
        description = description,
        status = status,
        pipeline = pipeline,
        result = result,
        created = created,
        updated = updated
      ),
      id
    )
  }

}
