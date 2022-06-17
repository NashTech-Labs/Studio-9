package cortex.jobmaster.tunning

import java.util.Date

import cortex.TaskTimeInfo
import cortex.jobmaster.common.json4s.CortexJson4sSupport
import cortex.jobmaster.jobs.job.cross_validation.CrossValidationJob.CrossValidationParams
import cortex.jobmaster.jobs.job.cross_validation.{ CrossValidationJob, CrossValidationJobConfig }
import cortex.jobmaster.jobs.tuning.HyperParamSelector.HPSelectorParams
import cortex.jobmaster.jobs.tuning.{ HyperParamRandomSearch, HyperParamSelector }
import cortex.scheduler.TaskScheduler
import cortex.task.tabular_data.tabularpipeline.TabularPipelineModule
import cortex.task.tabular_data.{ AllowedModelPrimitive, AllowedTaskType }
import cortex.task.transform.splitter.SplitterParams.SplitterTaskResult
import cortex.task.{ IntHyperParam, StorageAccessParams }
import cortex.testkit.FutureTestUtils
import org.mockito.Matchers.any
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.{ BeforeAndAfterAll, FlatSpec }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//TODO amplify tests

class HyperParamSelectorTest extends FlatSpec
  with BeforeAndAfterAll
  with CortexJson4sSupport
  with MockFactory
  with FutureTestUtils {

  private val hyperParamRandomSearch = new HyperParamRandomSearch() {
    override protected def randomElement[A](xs: Seq[A]): A = {
      xs.head
    }

    override protected def randomElement(lowBound: Double, upperBound: Double): Double = {
      lowBound
    }
  }
  private val crossValJob = new CrossValidationJob(any[TaskScheduler], any[TabularPipelineModule], any[StorageAccessParams], any[CrossValidationJobConfig]) {
    val tasksTimeInfo = Seq(TaskTimeInfo("task_id", new Date(), None, None))

    override def getCVScore(
      jobId:          String,
      splitterResult: SplitterTaskResult,
      cvParams:       CrossValidationParams,
      customPath:     Option[String]        = None
    ) = {
      cvParams.modelPrimitive match {
        case AllowedModelPrimitive.Linear       => Future.successful((1.0, tasksTimeInfo))
        case AllowedModelPrimitive.RandomForest => Future.successful((2.0, tasksTimeInfo))
        case AllowedModelPrimitive.Logistic     => Future.successful((0.0, tasksTimeInfo))
        case AllowedModelPrimitive.XGBoost      => Future.successful((4.0, tasksTimeInfo))
      }
    }
  }
  val hyperParamSelector = new HyperParamSelector(crossValJob, hyperParamRandomSearch)

  "Hyper param selector" should "select best model for regressor" in {
    val regressorParams = HPSelectorParams(
      AllowedTaskType.Regressor,
      any[Option[String]],
      any[String],
      any[Seq[String]],
      any[Seq[String]],
      any[String],
      any[String]
    )
    val numSamples = 10
    val (bestParams, _) = hyperParamSelector.getBestHyperParams(
      jobId            = any[String],
      numHPSamples     = numSamples,
      splitterResult   = any[SplitterTaskResult],
      hpSelectorParams = regressorParams
    ).await()
    bestParams.bestScore.score shouldBe 2.0
    bestParams.bestScore.modelWithHyperParams.hyperParams shouldBe Map(
      "n_estimators" -> IntHyperParam(100),
      "min_samples_leaf" -> IntHyperParam(1),
      "max_features" -> IntHyperParam(1)
    )
    bestParams.history.size shouldBe 20
  }

  it should "select best model for classifier" in {
    val classifierParams = HPSelectorParams(
      AllowedTaskType.Classifier,
      any[Option[String]],
      any[String],
      any[Seq[String]],
      any[Seq[String]],
      any[String],
      any[String]
    )
    val numSamples = 10
    val (bestParams, _) = hyperParamSelector.getBestHyperParams(
      jobId            = any[String],
      numHPSamples     = numSamples,
      splitterResult   = any[SplitterTaskResult],
      hpSelectorParams = classifierParams
    ).await()
    bestParams.bestScore.score shouldBe 2.0
    bestParams.bestScore.modelWithHyperParams.hyperParams.size shouldBe 3
    bestParams.bestScore.modelWithHyperParams.hyperParams.keys.toSet shouldBe Set("n_estimators", "min_samples_leaf", "max_features")
    bestParams.history.size shouldBe 10
  }
}
