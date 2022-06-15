package cortex.task.tabular_data

import cortex.JsonSupport
import cortex.testkit.OptionHelpers._
import cortex.task.tabular_data.tabularpipeline.TabularModelSummary.{ TabularClassifierSummary, TabularModelSummary, TabularRegressorSummary }
import org.scalatest.FlatSpec
import cortex.task.tabular_data.TabularSummaryTest._
import org.scalactic.TolerantNumerics
import org.scalatest.Matchers._
import play.api.libs.json.Reads

class TabularSummaryTest extends FlatSpec {
  implicit val doubleEquality = TolerantNumerics.tolerantDoubleEquality(0.01)

  val rawRegressorSummary = """ {
    "summary_stats": {
      "rmse": 0.045290501756894044,
      "r_squared": 0.9689634771170343,
      "mape": 4.500867803179555,
      "formula": "ycol ~ first + third + fourth",
      "variable_info": [
        {
          "variable_name": "fourth",
          "value_type": "Coefficient",
          "value": 0.18113726060159058,
          "stderr": 0.009273157084398896,
          "tvalue": 19.533505035338482,
          "pvalue": 0.0
        },
        {
          "variable_name": "first_test",
          "value_type": "Coefficient",
          "value": 0.179397378133375
        }
      ]
    }}"""

  val rawBinaryClassifierSummary = """ {"summary_stats": {
    "ks_statistic": 56.716576631624896,
    "roc_fpr": [0.0, 0.3148984198645598, 1.0],
    "roc_tpr": [0.0005558643690939411, 0.30183435241801, 1.0],
    "auc": 0.8771266831209212,
    "threshold": 0.4590396655351252,
    "f1_score": 0.8006439495572847,
    "precision": 0.7738589211618258,
    "recall": 0.8293496386881601,
    "confusion_matrix": [1492, 436, 307, 1336],
    "labels": ["0", "1"],
    "formula": "LogRings ~ Sex + Diameter + Height + Whole_Weight + Shucked_Weight + Viscera_Weight + Shell_Weight",
    "variable_info": [
          {
            "variable_name": "Diameter",
            "value_type": "Coefficient",
            "value": -0.06421419644308514,
            "stderr": 0.23061753901321932,
            "tvalue": -0.2784445481373569,
            "pvalue": 0.7806711265585269
          },
          {
            "variable_name": "Height",
            "value_type": "Coefficient",
            "value": 0.40916366900412054,
            "stderr": 0.1566082764441502,
            "tvalue": 2.6126567400799976,
            "pvalue": 0.008984148565819305
          }
    ]
  }} """

  val rawClassifierSummary = """ {"summary_stats": {
    "confusion_matrix": [1492, 436, 307, 1336],
    "labels": ["0", "1"]
  }} """

  "json serializer" should "deserialize regressor summary" in {
    val summary = JsonSupport.fromString[SummaryTestClass](rawRegressorSummary)
      .summaryStats
      .asInstanceOf[TabularRegressorSummary]
    assert(summary.rmse === 0.045)
    assert(summary.rSquared === 0.968)
    assert(summary.mape === 4.5)

    summary.formula.getMandatory shouldBe "ycol ~ first + third + fourth"
    val variableInfo = summary.variableInfo.getMandatory
    variableInfo.size shouldBe 2
    variableInfo(0).stderr.isDefined shouldBe true
    variableInfo(0).tvalue.isDefined shouldBe true
    variableInfo(0).pvalue.isDefined shouldBe true
    variableInfo(1).stderr.isDefined shouldBe false
    variableInfo(1).tvalue.isDefined shouldBe false
    variableInfo(1).pvalue.isDefined shouldBe false
  }

  "json serializer" should "deserialize binary classifier summary" in {
    val summary = JsonSupport.fromString[SummaryTestClass](rawBinaryClassifierSummary)
      .summaryStats
      .asInstanceOf[TabularClassifierSummary]

    summary.confusionMatrix.size shouldBe 4
    summary.labels.size shouldBe 2
    summary.binaryClassifierSummary.isDefined shouldBe true
    summary.variableInfo.getMandatory.size shouldBe 2
    summary.formula.getMandatory shouldBe "LogRings ~ Sex + Diameter + Height + Whole_Weight + Shucked_Weight + Viscera_Weight + Shell_Weight"
  }

  "json serializer" should "deserialize classifier summary" in {
    val summary = JsonSupport.fromString[SummaryTestClass](rawClassifierSummary)
      .summaryStats
      .asInstanceOf[TabularClassifierSummary]

    summary.confusionMatrix.size shouldBe 4
    summary.labels.size shouldBe 2
    summary.variableInfo.isEmpty shouldBe true
    summary.binaryClassifierSummary.isEmpty shouldBe true
    summary.formula.isEmpty shouldBe true
  }
}

object TabularSummaryTest {
  case class SummaryTestClass(summaryStats: TabularModelSummary)

  implicit val summaryTestClassReads: Reads[SummaryTestClass] = JsonSupport.SnakeJson.reads[SummaryTestClass]
}
