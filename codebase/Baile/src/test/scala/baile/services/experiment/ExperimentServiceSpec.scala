package baile.services.experiment

import java.time.Instant
import java.util.UUID

import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.cv.model.CVModelDao
import baile.dao.experiment.ExperimentDao
import baile.dao.images.AlbumDao
import baile.dao.table.TableDao
import baile.daocommons.{ EntityDao, WithId }
import baile.domain.asset.{ Asset, AssetReference, AssetType }
import baile.domain.common.CortexModelReference
import baile.domain.cv.model.{ CVModel, CVModelStatus, CVModelType }
import baile.domain.experiment.pipeline.ExperimentPipeline
import baile.domain.experiment.result.ExperimentResult
import baile.domain.images.Album
import baile.domain.images.AlbumLabelMode.Classification
import baile.domain.images.AlbumStatus.Active
import baile.domain.images.AlbumType.Source
import baile.domain.table.{ Table, TableStatisticsStatus, TableStatus, TableType }
import baile.services.asset.sharing.AssetSharingService
import baile.services.experiment.ExperimentService.ExperimentServiceError
import baile.services.experiment.PipelineHandler.PipelineCreatedResult
import baile.services.process.ProcessService
import baile.services.process.util.ProcessRandomGenerator
import baile.services.project.ProjectService
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._

class ExperimentServiceSpec extends ExtendedBaseSpec {

  trait Setup {

    val dao = mock[ExperimentDao]
    val projectService = mock[ProjectService]
    val processService = mock[ProcessService]
    val assetSharingService = mock[AssetSharingService]
    val experimentDelegator = mock[ExperimentDelegator]
    val tableDao = mock[TableDao]
    val albumDao = mock[AlbumDao]
    val cvModelDao = mock[CVModelDao]

    val experimentService = new ExperimentService(
      dao = dao,
      projectService = projectService,
      processService = processService,
      assetSharingService = assetSharingService,
      experimentDelegator = experimentDelegator
    ) {
      override def daoByAssetType(assetType: AssetType): EntityDao[_ <: Asset[_]] = assetType match {
        case AssetType.Table => tableDao
        case AssetType.Album => albumDao
        case AssetType.CvModel => cvModelDao
        case _ => throw new RuntimeException(s"Invalid asset type $assetType")
      }
    }

    implicit val user = SampleUser
    val tableId = randomString()
    val albumId = randomString()
    val modelId = randomString()
    val tableWithId = WithId(
      Table(
        ownerId = SampleUser.id,
        name = "name",
        repositoryId = "repositoryId",
        databaseId = "databaseId",
        created = Instant.now(),
        updated = Instant.now(),
        status = TableStatus.Active,
        columns = Seq(),
        `type` = TableType.Source,
        size = Some(0l),
        inLibrary = true,
        tableStatisticsStatus = TableStatisticsStatus.Pending,
        description = None
      ),
      tableId
    )
    val albumWithId = WithId(
      Album(
        ownerId = SampleUser.id,
        name = "name",
        status = Active,
        `type` = Source,
        labelMode = Classification,
        inLibrary = false,
        created = Instant.now(),
        updated = Instant.now(),
        picturesPrefix = "albums/name",
        description = Some("description"),
        augmentationTimeSpentSummary = None
      ),
      albumId
    )
    val model = WithId(
      CVModel(
        ownerId = UUID.randomUUID,
        name = "test Model",
        created = Instant.now(),
        updated = Instant.now(),
        status = CVModelStatus.Training,
        cortexFeatureExtractorReference = Some(CortexModelReference("cid", "cfilepath")),
        cortexModelReference = Some(CortexModelReference("cid", "cfilepath")),
        `type` = CVModelType.TL(CVModelType.TLConsumer.Classifier("FCN_1"), "SCAE"),
        classNames = Some(Seq("foo")),
        featureExtractorId = Some("fe_id"),
        description = Some("Model description"),
        inLibrary = true,
        experimentId = Some("experimentId")
      ),
      modelId
    )

    val experimentResult = new ExperimentResult {
      override def getAssetReferences: Seq[AssetReference] = Seq(
        AssetReference(tableId, AssetType.Table),
        AssetReference(albumId, AssetType.Album),
        AssetReference(modelId, AssetType.CvModel)
      )
    }

    val experiment = ExperimentRandomGenerator.randomExperiment(
      pipeline = new ExperimentPipeline {
        override def getAssetReferences = Seq.empty
      },
      ownerId = user.id,
      result = Some(experimentResult)
    )
  }

  "ExperimentService#create" should {

    "create new experiment" in new Setup {

      dao.count(*) shouldReturn future(0)
      experimentDelegator.validateAndCreatePipeline(*, experiment.entity.name, *) shouldReturn future {
        PipelineCreatedResult(
        { _: String => future(ProcessRandomGenerator.randomProcess()) },
        experiment.entity.pipeline
        ).asRight
      }
      dao.create(*) shouldReturn future(experiment)

      whenReady(
        experimentService.create(
          name = Some(experiment.entity.name),
          description = None,
          pipeline = experiment.entity.pipeline
        )
      ) { result =>
        result.right.value shouldBe experiment
      }

    }

    "return error when experiment name is taken" in new Setup {

      dao.count(*) shouldReturn future(1)

      whenReady(
        experimentService.create(
          name = Some(experiment.entity.name),
          description = None,
          pipeline = experiment.entity.pipeline
        )
      ) { result =>
        result.left.value shouldBe ExperimentServiceError.NameIsTaken
      }
    }

  }

  "ExperimentService#update" should {

    "update experiment name and description" in new Setup {

      val newName = "name"
      val newDesc = "desc"

      val updatedExperiment = experiment.copy(
        entity = experiment.entity.copy(name = newName, description = Some(newDesc))
      )

      dao.get(experiment.id) shouldReturn future(Some(experiment))
      dao.count(*) shouldReturn future(0)
      dao.update(experiment.id, *) shouldReturn future(Some(updatedExperiment))

      whenReady(
        experimentService.update(
          id = experiment.id,
          newName = Some(newName),
          newDescription = Some(newDesc)
        )
      ) { result =>
        result.right.value shouldBe updatedExperiment
      }

    }

  }

  "ExperimentService#preDelete" should {

    "invoke cleanup method on delegator" in new Setup {
      tableDao.get(tableWithId.id) shouldReturn future(Some(tableWithId))
      albumDao.delete(albumId) shouldReturn future(true)
      albumDao.get(albumWithId.id) shouldReturn future(Some(albumWithId))
      cvModelDao.get(model.id) shouldReturn future(Some(model))
      cvModelDao.update(model.id, *) shouldReturn future(Some(model))
      assetSharingService.deleteSharesForAsset(experiment.id, AssetType.Experiment) shouldReturn future(())
      projectService.removeAssetFromAllProjects(AssetReference(
        experiment.id,
        AssetType.Experiment
      )) shouldReturn future(())
      processService.cancelProcesses(experiment.id, AssetType.Experiment) shouldReturn future(().asRight)

      whenReady(experimentService.preDelete(experiment)) { result =>
        assert(result.isRight)
      }

    }

  }

}
