package cortex.task.tabular_data

import cortex.JsonSupport.SnakeJson
import play.api.libs.json.{ JsValue, Writes }

/**
 * Model primitives allowed in Tabular Pipeline
 */
sealed trait AllowedModelPrimitive {
  val stringValue: String
}

object AllowedModelPrimitive {
  case object Linear extends AllowedModelPrimitive {
    override val stringValue: String = {
      "linear"
    }
  }

  case object Logistic extends AllowedModelPrimitive {
    override val stringValue: String = {
      "logistic"
    }
  }

  case object RandomForest extends AllowedModelPrimitive {
    override val stringValue: String = {
      "randomForest"
    }
  }

  case object XGBoost extends AllowedModelPrimitive {
    override val stringValue: String = {
      "XGBoost"
    }
  }

  implicit object AllowedModelPrimitiveWrites extends Writes[AllowedModelPrimitive] {
    override def writes(amp: AllowedModelPrimitive): JsValue = SnakeJson.toJson(amp.stringValue)
  }
}

/**
 * Allowed task types in Tabular Pipeline
 */
sealed trait AllowedTaskType {
  val stringValue: String
}

object AllowedTaskType {
  case object Regressor extends AllowedTaskType {
    override val stringValue: String = {
      "regressor"
    }
  }

  case object Classifier extends AllowedTaskType {
    override val stringValue: String = {
      "classifier"
    }
  }

  implicit object AllowedTaskTypeWrites extends Writes[AllowedTaskType] {
    override def writes(att: AllowedTaskType): JsValue = SnakeJson.toJson(att.stringValue)
  }
}
