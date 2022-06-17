package cortex.jobmaster.jobs.tuning

import cortex.task.{ DoubleHyperParam, HyperParam, IntHyperParam }
import cortex.task.tabular_data.{ AllowedModelPrimitive, AllowedTaskType }

import scala.util.Random

class HyperParamRandomSearch(
    random: Random = new scala.util.Random(System.currentTimeMillis())
) {

  import HyperParamRandomSearch._

  protected def randomElement[A](xs: Seq[A]): A = {
    xs(random.nextInt(xs.length))
  }

  protected def randomElement(bounds: (Double, Double)): Double = bounds match {
    case (lowerBound, upperBound) => randomElement(lowerBound, upperBound)
  }

  protected def randomElement(lowerBound: Double, upperBound: Double): Double = {
    assert(upperBound > lowerBound)
    val range = upperBound - lowerBound
    Random.nextDouble() * range + lowerBound
  }

  def getHyperParams(taskType: AllowedTaskType, numSample: Int): Seq[ModelWithHyperParams] = {
    def getModelPrimitiveHP(nsamples: Int)(modelPrimitive: AllowedModelPrimitive) = modelPrimitive match {
      case AllowedModelPrimitive.Linear       => linearRegression(nsamples)
      case AllowedModelPrimitive.Logistic     => logisticRegression(nsamples)
      case AllowedModelPrimitive.RandomForest => randomForest(nsamples)
      case AllowedModelPrimitive.XGBoost      => xgboost(nsamples)
    }

    val modelPrimitives = taskType match {
      case AllowedTaskType.Regressor =>
        Seq(AllowedModelPrimitive.Linear, AllowedModelPrimitive.RandomForest)
      case AllowedTaskType.Classifier =>
        Seq(AllowedModelPrimitive.RandomForest)
    }

    val paramGrid = modelPrimitives.flatMap(getModelPrimitiveHP(numSample))
    paramGrid
  }

  /**
   * Elastic net linear regression. Map keys must be consistent with specs from:
   * http://scikit-learn.org/stable/modules/generated/sklearn.linear_model.ElasticNet.html
   */
  protected def linearRegression(numSample: Int): Seq[ModelWithHyperParams] = {
    val alphaMin = 1e-2
    val alphaMax = 1e+2

    // compute numAlphaSample values of alpha which form a GP
    val alphaSeq = {
      numSample match {
        case 0 =>
          Seq.empty[Double]

        case 1 =>
          Seq(1.0)

        case _ =>
          val cd = (math.log(alphaMax) - math.log(alphaMin)) / (numSample - 1)
          // (cd / 10.0) term ensures numAlphaSample values
          (math.log(alphaMin) to (math.log(alphaMax) + cd / 10.0) by cd).map(math.exp)
      }
    }

    for {
      alpha <- alphaSeq
    } yield ModelWithHyperParams(AllowedModelPrimitive.Linear, Map("alpha" -> DoubleHyperParam(alpha)))
  }

  /**
   * L2 regularized logistic regression. Map keys should be consistent with:
   * http://scikit-learn.org/stable/modules/generated/sklearn.linear_model.LogisticRegression.html
   */
  protected def logisticRegression(numSample: Int): Seq[ModelWithHyperParams] = {
    val cMin = 1e-2
    val cMax = 1e+4
    // Grid search over C values. C is inverse regularization strength
    val cValues =
      if (numSample == 1) {
        Seq(1.0)
      } else {
        val cd = (math.log(cMax) - math.log(cMin)) / (numSample - 1)
        // (cd / 10.0) term ensures nsample values
        (math.log(cMin) to (math.log(cMax) + cd / 10.0) by cd).map(math.exp)
      }

    cValues.map(c => ModelWithHyperParams(AllowedModelPrimitive.Logistic, Map("C" -> DoubleHyperParam(c))))
  }

  /**
   * randomForest classification & regression. Map keys should be consistent with:
   * http://scikit-learn.org/stable/modules/generated/sklearn.ensemble.RandomForestRegressor.html
   */
  protected def randomForest(numSample: Int): Seq[ModelWithHyperParams] = {
    //scalastyle: off
    val numEstimators = Seq(100, 200, 400)
    val minSamplesLeaf = Seq(1, 5) ++ (10 to 100)
    val maxFeatures = 1 to 100
    //scalastyle: on

    (0 until numSample).map { _ =>
      ModelWithHyperParams(AllowedModelPrimitive.RandomForest, Map(
        "n_estimators" -> IntHyperParam(randomElement(numEstimators)),
        "min_samples_leaf" -> IntHyperParam(randomElement(minSamplesLeaf)),
        "max_features" -> IntHyperParam(randomElement(maxFeatures))
      ))
    }
  }

  /**
   * XGBoost classification & regression
   * Refs:
   * https://www.slideshare.net/ShangxuanZhang/kaggle-winning-solution-xgboost-algorithm-let-us-learn-from-its-author
   * https://www.analyticsvidhya.com/blog/2016/03/complete-guide-parameter-tuning-xgboost-with-codes-python/
   * http://xgboost.readthedocs.io/en/latest/python/python_api.html#xgboost.XGBRegressor
   */
  protected def xgboost(numSample: Int): Seq[ModelWithHyperParams] = {
    //scalastyle: off
    val numEstimators = Seq(100, 200, 400)
    val learningRate = (0.05, 0.6) // also eta in some docs of xgboost API
    val gamma = (0.0, 0.5)
    val minChildWeight = 1 to 6
    val maxDepth = 3 to 10
    val maxDeltaStep = Seq(0, 1)
    val subsample = (0.5, 1.0)
    val colsampleByTree = (0.5, 1.0)
    //scalastyle: on

    (0 until numSample).map { _ =>
      ModelWithHyperParams(AllowedModelPrimitive.XGBoost, Map(
        "n_estimators" -> IntHyperParam(randomElement(numEstimators)),
        "learning_rate" -> DoubleHyperParam(randomElement(learningRate)),
        "gamma" -> DoubleHyperParam(randomElement(gamma)),
        "min_child_weight" -> IntHyperParam(randomElement(minChildWeight)),
        "max_depth" -> IntHyperParam(randomElement(maxDepth)),
        "max_delta_step" -> IntHyperParam(randomElement(maxDeltaStep)),
        "subsample" -> DoubleHyperParam(randomElement(subsample)),
        "colsample_bytree" -> DoubleHyperParam(randomElement(colsampleByTree))
      ))
    }
  }
}

object HyperParamRandomSearch {
  case class ModelWithHyperParams(
      modelPrimitive: AllowedModelPrimitive,
      hyperParams:    Map[String, HyperParam]
  )
}
