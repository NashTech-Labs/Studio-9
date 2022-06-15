package baile.services.pipeline

import java.time.Instant
import java.util.UUID

import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.pipeline.{ PipelineDao, PipelineOperatorDao }
import baile.daocommons.WithId
import baile.domain.asset.sharing.SharedResource
import baile.services.asset.sharing.SampleSharedAccessChecker
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.pipeline.PipelineParams.{ StringParam, StringParams }
import baile.domain.pipeline._
import baile.services.usermanagement.util.TestData
import cats.implicits._
import org.scalatest.prop.TableDrivenPropertyChecks

class PipelineSharedAccessSpec extends ExtendedBaseSpec with TableDrivenPropertyChecks {

  trait Setup {
    val pipelineDaoMock = mock[PipelineDao]
    val pipelineOperatorDaoMock = mock[PipelineOperatorDao]

    val checker = new SampleSharedAccessChecker with PipelineSharedAccess {
      override protected val pipelineDao: PipelineDao = pipelineDaoMock
      override protected val pipelineOperatorDao: PipelineOperatorDao = pipelineOperatorDaoMock
    }

    val operatorId = randomString()
    val albumId = randomString()
    val tabularModelId = randomString()
    val dCProjectId = randomString()
    val tableId = randomString()
    val onlineJobId = randomString()
    val datasetId = randomString()
    val cvPredictionId = randomString()
    val cvModelId = randomString()

    val notAssetId = randomString()

    val assetParams = Seq(
      (AssetType.TabularModel, tabularModelId, "tabularModelParam"),
      (AssetType.DCProject, dCProjectId, "dCProjectParam"),
      (AssetType.Table, tableId, "tableParam"),
      (AssetType.Album, albumId, "albumParam"),
      (AssetType.OnlineJob, onlineJobId, "onlineJobParam"),
      (AssetType.Dataset, datasetId, "datasetParam"),
      (AssetType.CvPrediction, cvPredictionId, "cvPredictionParam"),
      (AssetType.CvModel, cvModelId, "cvModelParam")
    )

    val pipelineOperatorParams = assetParams.map {
      case (assetType, _, name) => createAssetParameter(name, assetType)
    }

    val pipelineOperator =  WithId(
      PipelineOperator(
        name = randomString(),
        description = None,
        category = Some(randomString()),
        className = randomString(),
        moduleName = randomString(),
        packageId = randomString(),
        inputs = Seq(),
        outputs = Seq(),
        params = pipelineOperatorParams
      ),
      operatorId
    )

    val pipeline = WithId(
      Pipeline(
        ownerId = UUID.randomUUID,
        name = randomString(),
        created = Instant.now,
        updated = Instant.now,
        status = PipelineStatus.Idle,
        description = Some(randomString()),
        inLibrary = true,
        steps = Seq(
          PipelineStepInfo(
            step = PipelineStep(
              id = randomString(),
              operatorId = operatorId,
              inputs = Map(),
              params = assetParams.map {
                case (_, assetId, name) => name -> StringParam(assetId)
              }.toMap ++ Map(
                "cvModelParam" -> StringParams(Seq(randomString(), randomString(), cvModelId)),
                "notAssetParam" -> StringParam(notAssetId)
              ),
              coordinates = None
            ),
            pipelineParameters = Map.empty
          )
        )
      ),
      randomString()
    )

    val sharedPipeline = SharedResource(
      ownerId = pipeline.entity.ownerId,
      name = Some(pipeline.entity.name),
      created = Instant.now,
      updated = Instant.now,
      recipientId = Some(TestData.SampleUser.id),
      recipientEmail = Some(TestData.SampleUser.email),
      assetType = AssetType.Pipeline,
      assetId = pipeline.id
    )

    def createAssetParameter(name: String, assetType: AssetType) =
      OperatorParameter(
        name = name,
        description = Some(randomString()),
        multiple = randomBoolean(),
        typeInfo = AssetParameterTypeInfo(
          assetType = assetType
        ),
        conditions = Map.empty[String, ParameterCondition]
      )
  }

  "PipelineSharedAccess#checkSharedAccess" should {

    "provide access for all asset types if pipeline is shared" in new Setup {

      pipelineDaoMock.get(pipeline.id)(*) shouldReturn future(Some(pipeline))
      pipelineOperatorDaoMock.get(operatorId)(*) shouldReturn future(Some(pipelineOperator))

      forAll(
        Table(
          ("asset type", "asset id"),
          assetParams.map {
            case (assetType, assetId, _) => assetType -> assetId
          }: _*
        )
      ) { (assetType, assetId) =>
        whenReady(
          checker.checkSharedAccess(
            AssetReference(assetId, assetType),
            sharedPipeline
          ).value
        )(_ shouldBe ().asRight)
      }
    }

    "return left for wrong asset type" in new Setup {

      pipelineDaoMock.get(pipeline.id)(*) shouldReturn future(Some(pipeline))
      pipelineOperatorDaoMock.get(operatorId)(*) shouldReturn future(Some(pipelineOperator))

      whenReady(
        checker.checkSharedAccess(
          AssetReference(albumId, AssetType.CvModel),
          sharedPipeline
        ).value
      )(_ shouldBe ().asLeft)
    }

    "return left for not asset operator parameter" in new Setup {

      pipelineDaoMock.get(pipeline.id)(*) shouldReturn future(Some(pipeline))
      pipelineOperatorDaoMock.get(operatorId)(*) shouldReturn future(Some(pipelineOperator))

      whenReady(
        checker.checkSharedAccess(
          AssetReference(notAssetId, AssetType.Album),
          sharedPipeline
        ).value
      )(_ shouldBe ().asLeft)
    }

    "return left for wrong shared resource asset type" in new Setup {
      whenReady(
        checker.checkSharedAccess(
          AssetReference(randomString(), AssetType.Pipeline),
          sharedPipeline.copy(assetType = AssetType.CvModel)
        ).value
      )(_ shouldBe ().asLeft)
    }

  }
}
