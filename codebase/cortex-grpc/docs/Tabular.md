[Tabular](../src/main/proto/cortex/api/job/tabular.proto)
======

Example messages can be found in [Sample.scala](../src/main/scala/cortex/api/job/tabular/Sample.scala)

ColumnMapping
------
Represents mapping from column name which was used in training table to the name from current table, in case they are not the same

| Field name | Description | Mandatory |
|---|---|---|
| train_name | Original name of this column which was used for training | yes |
| current_name | Name of this column in the current table | yes |

ModelType
------
| Value |
|---|
| REGRESSION |
| BINARY |
| MULTICLASS |

TrainRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| input | Data source to read rows for train from. It should contain all the columns which are specified in this request | yes |
| predictors | Set of predictors columns | yes |
| response | Response column | yes |
| weight | Special column that indicates training weight of row | no |
| output | Data source to put initial prediction result to. This data source should **not** exist for the moment of sending this request | yes |
| drop_previous_result_table | Drop previous results from Redshift (by default, a task will fail if results exist) | no |
| prediction_result_column_name | Name of the column for output data source for prediction result values | yes |
| probability_columns_prefix | Prefix which should be prepended for all probability column names in case of classification model | no |

ClassConfusion
------
Contains standard machine learning summary metrics for a given class

| Field name | Description | Mandatory |
|---|---|---|
| class_name | Name | yes |
| true_positive | Number of true positive occurrences | yes |
| true_negative | Number of true negative occurrences | yes |
| false_positive | Number of false positive occurrences | yes |
| false_negative | Number of false negative occurrences | yes |

ParametricModelPredictorSummary
------
A set of machine learning summary metrics for predictor of a model which were trained using parametric algorithm, such as linear or logistric regression

| Field name | Description | Mandatory |
|---|---|---|
| coefficient | http://dss.princeton.edu/online_help/analysis/interpreting_regression.htm#coefficients | yes |
| std_err | http://dss.princeton.edu/online_help/analysis/interpreting_regression.htm#ptse. May be not present in case of computation error | no |
| t_value | http://dss.princeton.edu/online_help/analysis/interpreting_regression.htm#ptse. May be not present in case of computation error | no |
| p_value | http://dss.princeton.edu/online_help/analysis/interpreting_regression.htm#ptse. May be not present in case of computation error | no |

TreeModelPredictorSummary
------
A set of machine learning summary metrics for predictor of a model which were trained using tree-based algorithm

| Field name | Description | Mandatory |
|---|---|---|
| importance | https://www.ibm.com/support/knowledgecenter/ko/SSLVMB_20.0.0/com.ibm.spss.statistics.help/alg_tree-cart_variable-importance.htm | yes |

PredictorSummary
------
| Field name | Description | Mandatory |
|---|---|---|
| name | Name of the predictor | yes |
| summary | Either **ParametricModelPredictorSummary** or **TreeModelPredictorSummary** | yes |

RegressionSummary
------
A set of machine learning summary metrics for regression model

| Field name | Description | Mandatory |
|---|---|---|
| rmse | https://en.wikipedia.org/wiki/Mean_squared_error | yes |
| r2 | https://en.wikipedia.org/wiki/Coefficient_of_determination | yes |
| mape | https://en.wikipedia.org/wiki/Mean_absolute_percentage_error | yes |

RocValue
------
| Field name | Description | Mandatory |
|---|---|---|
| true_positive | True positive occurrence | yes |
| false_positive | False positive occurrence | yes |

ClassificationSummary
------
General set of machine learning summary metrics for classification model

| Field name | Description | Mandatory |
|---|---|---|
| confusion_matrix | Confusion matrix | yes |

BinaryClassificationEvalSummary
------
A set of machine learning summary metrics for binary classification model for evaluate stage

| Field name | Description | Mandatory |
|---|---|---|
| general_classification_summary | General classification summary | yes |
| ks | Kolmogorov-Smirnov metric | yes |

BinaryClassificationTrainSummary
------
A set of machine learning summary metrics for binary classification model for train stage

| Field name | Description | Mandatory |
|---|---|---|
| area_under_roc | Area under ROC | yes |
| roc_values | Sequence of ROC values | yes |
| f1_score | F1 score | yes |
| precision | Precision | yes |
| recall | Recall | yes |
| threshold | Threshold | yes |
| binary_classification_eval_summary | **BinaryClassificationEvalSummary** | yes |

TrainResult
------
| Field name | Description | Mandatory |
|---|---|---|
| model_id | Reference to the trained model created by the system. This will be used later for predict/evaluate actions | yes |
| model_type | Type of model | yes |
| formula | Formula of model | no |
| summary | Either **ClassificationSummary**, **BinaryClassificationTrainSummary** or **RegressionSummary** | yes |
| predictors_summary | Set of predictors summaries | yes |
| probability_columns | Sequence of **K** probability columns. Column name for those should all start with **probability_columns_prefix** from the corresponding **TrainRequest**  | yes | 
| output | Data source which contains initial prediction result. It should contain all the columns from the table which were provided in input data source and the new column for prediction result called as specified in **prediction_result_column_name** argument from the corresponding **TrainRequest**. Data type and variable type of this column should be the same as of **response** column which were present in the **TrainRequest**. In case of classification model type this data source should also include **K** additional columns. Names for these columns are given in **probability_columns** field and type for those is **DOUBLE**. These columns represent probabilities for each class  | yes |
| model_primitive | Primitive of model which were used for training, such as logistic, randomForest, svm, gbm, bayesian, etc | yes |
| model_file_path | Path to the serialized model | yes |

PredictRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| model_id | Id of the model | yes |
| input | Data source to read rows for prediction from. It should contain all the columns which are specified in this request | yes |
| output | Data source to put prediction result to. This data source should **not** exist for the moment of sending this request | yes |
| predictors | Set of predictors column mappings | yes |
| drop_previous_result_table | Drop previous results from Redshift (by default, a task will fail if results exist) | no |
| prediction_result_column_name | Name of the column for output data source for prediction result values | yes |
| probability_columns | Columns to put probability values for classes to. Only make sense for predictions for classification model | no |
| model_reference | **ClassReference** | yes |

PredictionResult
------
| Field name | Description | Mandatory |
|---|---|---|
| output | Data source which contains prediction result. It should contain at least all the columns which were provided in input data source and the new column called **$prediction_result$** (dollar sign is mandatory!). Data type and variable type of this column should be the same as of **response** column, which were used in model training. In case of classification model type this data source should also include **K** additional columns with names exactly the same as in **predicted_classes_column_names** field and type being **DOUBLE**. These columns represent probabilities for each class  | yes |

EvaluateRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| model_id | Id of the model | yes |
| input | Data source to read rows for evaluation from. It should contain all the columns which are specified in this request | yes |
| predictors | Set of predictors column mappings | yes |
| response | Response column mapping | yes |
| weight | Mapping of special column that indicates evaluation weight of row | no |
| output | Data source to put evaluation result to. This data source should **not** exist for the moment of sending this request | yes |
| drop_previous_result_table | Drop previous results from Redshift (by default, a task will fail if results exist) | no |
| prediction_result_column_name | Name of the column for output data source for prediction result values | yes |
| probability_columns | Columns to put probability values for classes to. Only make sense for evaluations for classification model | no |
| model_reference | **ClassReference** | yes |

EvaluationResult
------
| Field name | Description | Mandatory |
|---|---|---|
| summary | Either **ClassificationSummary**, **BinaryClassificationEvalSummary** or **RegressionSummary** | yes |
| output | Data source which contains evaluation result. It should contain at least all the columns which were provided in input data source and the new column called **prediction_result$** (dollar sign is mandatory!). Data type and variable type of this column should be the same as of **response** column which were present in EvaluateRequest. In case of classification model type this data source should also include **K** additional columns with names exactly the same as in **predicted_classes_column_names** field and type being **DOUBLE**. These columns represent probabilities for each class  | yes |

TabularModelImportRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| path | Location of an s3 file containing a model | yes |
| model_class_reference | **ClassReference** | yes |

TabularModelImportResult
------
| Field name | Description | Mandatory |
|---|---|---|
| tabular_model_reference | Reference to the imported model | yes |
