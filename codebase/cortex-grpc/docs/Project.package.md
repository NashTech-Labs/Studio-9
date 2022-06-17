[Project Package](../src/main/proto/cortex/api/job/project.package.proto)
======

Example messages can be found in [Sample.scala](../src/main/scala/cortex/api/job/project/package/Sample.scala)

ProjectPackageRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| project_files_path | Project files location | yes |
| name | Name of the package | yes |
| version | Version of the package | yes |
| target_prefix | Prefix which should be used to store the package file. Effectively, this is location, _where to put result files_  | yes |

ProjectPackageResponse
------
| Field name | Description | Mandatory |
|---|---|---|
| package_location | Complete package path. The full path where the package is saved | yes |
| cv_tl_model_primitives | List of CVTLModelPrimitives which were exposed for this packge | no |
| pipeline_operators | List of pipeline operators which were exposed for this packge | no |

CVTLModelPrimitive
------
| Field name | Description | Mandatory |
|---|---|---|
| name | Name | yes |
| description | Description | no |
| module_name | Full Python module name containing the class | yes |
| class_name | Name of the class | yes |
| operator_type | Operator type | yes |
| params | List of the parameters | yes |
| is_neural | Specifies whether model is neural or not | yes |

PipelineOperator
------
| Field name | Description | Mandatory |
|---|---|---|
| module_name |  Full Python module name containing the class | yes |
| class_name | Name of the class | yes |
| inputs | inputs of the operator | yes |
| outputs | outputs of the operator | yes |
| params | List of the parameters | yes |

PipelineOperatorInput
------
| Field name | Description | Mandatory |
|---|---|---|
| name | Name of the input | yes |
| description | Description of the input | no |
| type | **PipelineDataType** | yes |
| covariate | Flag to check its covariate or not | yes |
| required | Specifies whether the input is mandatory | yes |

PipelineOperatorOutput
------
| Field name | Description | Mandatory |
|---|---|---|
| description | Description of the output | yes |
| type | **PipelineDataType** | yes |

PipelineDataType
------
| Field name | Description | Mandatory |
|---|---|---|
| data_type | Primitive or Complex data type | yes |

PrimitiveDataType
------
| Value |
|---|
| String |
| Boolean |
| Float |
| Integer |

ComplexDataType
------
| Field name | Description | Mandatory |
|---|---|---|
| definition | definition of complex data type | yes |
| parents | list of complex data type | no |
| type_arguments | type arguments for the complex data type | yes |

OperatorType
------
| Value |
|---|
| UTLP |
| Classifier |
| Detector |

OperatorParameter
------
| Field name | Description | Mandatory |
|---|---|---|
| name | Name | yes |
| description | Description | no |
| multiple | Indicates whether this parameter can contain several values (array in Python) | yes |
| type_info | Additional type-depended information | yes |
| conditions | Describes when this parameter is needed based on conditions regarding other parameters. Key of this map is the name of a parameter. | no |

StringParameter
------
| Field name | Description | Mandatory |
|---|---|---|
| values | Possible values | no |
| defaults | Default values. For not __multiple__ params only first value should be taken into account if present | no |

IntParameter
------
| Field name | Description | Mandatory |
|---|---|---|
| values | Possible values | no |
| defaults | Default values. For not __multiple__ params only first value should be taken into account if present | no |
| min | Minimal possible value | no |
| max | Maximal possible value | no |
| step | Step for values between min and max | no |

FloatParameter
------
| Field name | Description | Mandatory |
|---|---|---|
| values | Possible values | no |
| defaults | Default values. For not __multiple__ params only first value should be taken into account if present | no |
| min | Minimal possible value | no |
| max | Maximal possible value | no |
| step | Step for values between min and max | no |

BooleanParameter
------
| Field name | Description | Mandatory |
|---|---|---|
| defaults | Default values. For not __multiple__ params only first value should be taken into account if present | no |

AssetParameter
------
| Field name | Description | Mandatory |
|---|---|---|
| asset_type | **AssetType** | yes |

ParameterCondition
------
Basically placeholder for one of following messages: __StringParameterCondition__, __IntParameterCondition__, __FloatParameterCondition__, __BooleanParameterCondition__

StringParameterCondition
------
| Field name | Description | Mandatory |
|---|---|---|
| values | Possible values | yes |

IntParameterCondition
------
| Field name | Description | Mandatory |
|---|---|---|
| values | Possible values | no |
| min | Minimal possible value | no |
| max | Maximal possible value | no |

FloatParameterCondition
------
| Field name | Description | Mandatory |
|---|---|---|
| values | Possible values | no |
| min | Minimal possible value | no |
| max | Maximal possible value | no |

BooleanParameterCondition
------
| Field name | Description | Mandatory |
|---|---|---|
| value | Possible value | yes |

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
| DCProject 
