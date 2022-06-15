package baile.dao.tabular.model

import java.time.Instant
import java.util.UUID

import baile.BaseSpec
import baile.domain.common.{ ClassReference, CortexModelReference }
import baile.domain.table.{ ColumnDataType, ColumnVariableType }
import baile.domain.tabular.model.summary._
import baile.domain.tabular.model.{ ModelColumn, TabularModel, TabularModelStatus }
import org.mongodb.scala.MongoDatabase
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.util.Success

class TabularModelDaoSpec extends BaseSpec with TableDrivenPropertyChecks {

  "TabularModelDao" should {
    val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
    val dao: TabularModelDao = new TabularModelDao(mockedMongoDatabase)

    "convert model with regression summary to document and back" in {
      serializeAndDeserialize(randomModel(
        trainSummary = randomRegressionSummary()
      ))
    }

    "convert model with classification summary to document and back" in {
      serializeAndDeserialize(randomModel(
        trainSummary = randomClassificationSummary()
      ))
    }

    "convert model with binary classification evaluation summary to document and back" in {
      serializeAndDeserialize(randomModel(
        evaluationSummary = Some(randomBinaryClassificationEvaluationSummary())
      ))
    }

    "convert model with binary classification train summary to document and back" in {
      serializeAndDeserialize(randomModel(
        trainSummary = randomBinaryClassificationTrainSummary()
      ))
    }

    "convert model with different statuses" in {
      forAll(
        Table(
          "status",
          TabularModelStatus.Active,
          TabularModelStatus.Saving,
          TabularModelStatus.Cancelled,
          TabularModelStatus.Error,
          TabularModelStatus.Predicting,
          TabularModelStatus.Training
        )
      ) { tabularModelStatus =>
        serializeAndDeserialize(randomModel(
          status = tabularModelStatus
        ))
      }
    }

    def serializeAndDeserialize(model: TabularModel) = {
      val document = dao.entityToDocument(model)
      val restoredModel = dao.documentToEntity(document)

      restoredModel shouldBe Success(model)
    }

  }

  private def randomModel(
    trainSummary: TabularModelTrainSummary = randomOf(
      randomRegressionSummary(),
      randomClassificationSummary(),
      randomBinaryClassificationTrainSummary()
    ),
    evaluationSummary: Option[TabularModelEvaluationSummary] = randomOf(
      None,
      Some(randomRegressionSummary()),
      Some(randomClassificationSummary()),
      Some(randomBinaryClassificationEvaluationSummary())
    ),
    status: TabularModelStatus = randomOf(
      TabularModelStatus.Active,
      TabularModelStatus.Cancelled,
      TabularModelStatus.Error,
      TabularModelStatus.Predicting,
      TabularModelStatus.Training,
      TabularModelStatus.Saving
    )
  ): TabularModel = TabularModel(
    ownerId = UUID.randomUUID(),
    name = randomString(),
    predictorColumns = Seq.fill(randomInt(10))(randomColumn()),
    responseColumn = randomColumn(),
    classNames = randomOf(None, Some(List(randomString(), randomString()))),
    classReference = randomClassReference(),
    cortexModelReference = Some(
      CortexModelReference(
        cortexId = randomString(),
        cortexFilePath = randomString()
      )
    ),
    inLibrary = randomBoolean(),
    status = status,
    created = Instant.now(),
    updated = Instant.now(),
    description = None,
    experimentId = None
  )

  private def randomClassReference() = ClassReference(
    moduleName = randomString(),
    className = randomString(),
    packageId = randomString()
  )

  private def randomColumn(): ModelColumn =
    ModelColumn(
      name = randomString(),
      displayName = randomString(),
      dataType = randomOf(
        ColumnDataType.Boolean,
        ColumnDataType.Double,
        ColumnDataType.Integer,
        ColumnDataType.Long,
        ColumnDataType.String,
        ColumnDataType.Timestamp
      ),
      variableType = randomOf(ColumnVariableType.Categorical, ColumnVariableType.Categorical)
    )

  private def randomRegressionSummary(): RegressionSummary =
    RegressionSummary(
      rmse = randomInt(999),
      r2 = randomInt(999),
      mape = randomInt(999)
    )

  private def randomClassificationSummary(): ClassificationSummary =
    ClassificationSummary(
      confusionMatrix = Seq.fill(randomInt(10))(
        ClassConfusion(
          className = randomString(),
          truePositive = randomInt(999),
          trueNegative = randomInt(999),
          falsePositive = randomInt(999),
          falseNegative = randomInt(999),
        )
      )
    )

  private def randomBinaryClassificationEvaluationSummary(): BinaryClassificationEvaluationSummary =
    BinaryClassificationEvaluationSummary(
      classificationSummary = randomClassificationSummary(),
      ks = randomInt(9999)
    )

  private def randomBinaryClassificationTrainSummary(): BinaryClassificationTrainSummary =
    BinaryClassificationTrainSummary(
      evaluationSummary = randomBinaryClassificationEvaluationSummary(),
      areaUnderROC = randomInt(9999),
      rocValues = Seq.fill(randomInt(10))(RocValue(randomInt(9999), randomInt(9999))),
      f1Score = randomInt(9999),
      precision = randomInt(9999),
      recall = randomInt(9999),
      threshold = randomInt(9999)
    )

}
