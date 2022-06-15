[Pipeline](../src/main/proto/cortex/api/job/pipeline.proto)
======

Example messages can be found in [Sample.scala](../src/main/scala/cortex/api/job/pipeline/Sample.scala)

PipelineRunRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| pipeline_steps_request | Array of step request for building a pipeline | yes |
| baile_auth_token | User's authentication token for baile | yes |

PipelineStepRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| step_id | Id of the step | yes |
| operator | **ClassReference** | yes |
| inputs | Describes the inputs for the operator, Key of this map is the name of the input. | yes |
| params | Describes the parameters required for the step, Key of this map is the name of the param | yes |

PipelineOutputReference
------
| Field name | Description | Mandatory |
|---|---|---|
| step_id | Id of the step | yes |
| output_index | Index of the output | yes |

PipelineParam
------
Basically placeholder for one of following type of params: 
__IntParam__, __IntSequenceParams__, __StringParam__, __StringSequenceParams__,
__FloatParam__, __FloatSequenceParams__, __BooleanParam__, __BooleanSequenceParams__,
__EmptySequenceParams__

StringSequenceParams
------
| Field name | Description | Mandatory |
|---|---|---|
| string_params | Array of string parameters | yes |

IntSequenceParams
------
| Field name | Description | Mandatory |
|---|---|---|
| int_params | Array of integer parameters | yes |

FloatSequenceParams
------
| Field name | Description | Mandatory |
|---|---|---|
| float_params | Array of float parameters | yes |

BooleanSequenceParams
------
| Field name | Description | Mandatory |
|---|---|---|
| boolean_params | Array of boolean parameters | yes |

OperatorApplicationSummary
------
Basically placeholder for one of following type of summary: 
__ConfusionMatrix__, __SimpleSummary__

SimpleSummary
------
| Field name | Description| Mandatory |
|---|---|---|
| values | Describes the pipeline values required for the simple summary, Key of this map is the name of the value | yes |

PipelineStepGeneralResponse
------
| Field name | Description | Mandatory |
|---|---|---|
| step_id | Id of the pipeline step | yes |
| tracked_asset_references | Array of assets saved within pipeline step | yes |
| summaries | Array of the OperatorApplicationSummary | yes |
| output_values| Describes the pipeline values required for the step result, Key of this map is the index of the output  | yes |
| step_execution_time | Time required to execute a step | yes |

PipelineStepFailureResponse
------
| Field name | Description | Mandatory |
|---|---|---|
| pipeline_step_general_response | General step response of a pipeline | yes |
| error_message | Error msg if pipeline fails | no |

PipelineStepResponse
------
Basically placeholder for one of following type of response: 
__PipelineStepGeneralResponse__, __PipelineStepFailureResponse__

PipelineRunResponse
------
| Field name | Description | Mandatory |
|---|---|---|
| pipeline_steps_response | Array of step response of a pipeline | yes |

TrackedAssetReference
------
| Field name | Description | Mandatory |
|---|---|---|
| asset_id | Id of the asset | yes |
| asset_type | Enum value of the type of asset | yes |

AssetType
------
| Value |
|---|
| TabularModel |
| TabularPrediction |
| Table |
| Flow |
| Album |
| CvModel |
| CvPrediction |
| OnlineJob |
| DCProject |
| Experiment |
| Pipeline |
