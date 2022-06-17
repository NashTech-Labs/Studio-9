[Computer Vision](../../src/main/proto/cortex/api/job/computervision.proto)
======

Example messages can be found in [Sample.scala](../src/main/scala/cortex/api/job/computervision/Sample.scala)

CVModelTrainRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| feature_extractor_id | Feature extractor identifier to be used for transfer learning | no |
| feature_extractor_class_reference | **ClassReference** | yes |
| images | List of labeled images which should be used for training | yes |
| file_path_prefix | Prefix for the images file paths. For each image, actual location is determined by combining this prefix with file path | yes |
| model_type | **TLModelType** | yes |
| augmentation_params | Params for augmentation to apply to images during the training | no |
| tune_feature_extractor | When true, apart from creating model, also apply changes to feature extractor, based on **images**. Implies feature_extractor_id to be specified and request type to be **TRANSFER_LEARNING** | no |
| model_parameters | Params for model module. Makes sense only to particular model implementation | no |
| feature_extractor_parameters | Params for FE module. Makes sense only to particular FE implementation | no |
| probability_prediction_table | **TableMeta** to create a table to put probability distribution to | no |
| input_size | **InputSize** to scale images to | no |
| labels_of_interest | List of labels which initial prediction should be done for, with their desired thresholds | no |
| default_visual_threshold | Default threshold for labels | no |
| iou_threshold | IoU threshold values for initial prediction evaluation | no |
| feature_extractor_learning_rate | Learning rate for feature extractor portion of the model | no |
| model_learning_rate | Learning rate for consumer portion of the model | no |

CVModelTrainResult
------
| Field name | Description | Mandatory |
|---|---|---|
| feature_extractor_reference | Reference to the feature extractor created by the system | yes |
| cv_model_reference | Reference to the trained model created by the system. This will be used later for predict/evaluate actions | no |
| images | List of initial predict result images. Used to see how well model was trained | yes |
| confusion_matrix | **ConfusionMatrix** | yes |
| map | Mean average precision | no |
| augmented_images | Sample sequence of images, which were created by augmenting input images if asked | no |
| augmentation_summary| Summary of the augmentations applied | no
| data_fetch_time | Time required to fetch input data from remote storage (s3) to be used by local processes (in seconds) | yes |
| training_time | Time required to train model (in seconds) | yes |
| save_model_time | Time required to save model (in seconds) | yes |
| prediction_time | Time required for prediction (in seconds) | yes |
| reconstruction_loss | Integral metric of autoencoder quality | no |
| pipeline_timings | Map of times spent on different stages of the pipeline. Keys are descriptions and values are times (in seconds) | yes |
| probability_prediction_table_schema | **ProbabilityPredictionTableSchema**. Required if `probabilityPredictionTable` is defined in a request | no |

PredictRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| model_type | **CVModelType** | yes |
| model_id | Id of the model | yes |
| images | List of untagged images to predict tags for | yes |
| file_path_prefix | Prefix for the images file paths. For each image, actual location is determined by combining this prefix with file path | yes |
| labels_of_interest | List of labels which prediction should be done for. If not presented, ALL labels which this model is trained for should be searched in input images | no |
| video_params | Params for assembling the video | no |
| target_prefix | Prefix for the output images file paths. For each image, actual location is determined by combining this prefix with file path | no |
| probability_prediction_table | **TableMeta** to create a table to put probability distribution to | no |
| default_visual_threshold | Default threshold for labels | no |

PredictResult
------
| Field name | Description | Mandatory |
|---|---|---|
| images | List of images with predicted tags | yes |
| video_file_size | Size of the assembled video, if asked to assemble in request | no |
| data_fetch_time | Time required to fetch input data from remote storage (s3) to be used by local processes (in seconds) | yes |
| load_model_time | Time required to load model (in seconds) | yes |
| prediction_time | Time required for prediction (in seconds) | yes |
| pipeline_timings | Map of times spent on different stages of the pipeline. Keys are descriptions and values are times (in seconds) | yes |
| probability_prediction_table_schema | **ProbabilityPredictionTableSchema**. Required if `probabilityPredictionTable` is defined in a request | no |

EvaluateRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| model_type | **CVModelType** | yes |
| model_id | Id of the model | yes |
| images | List of tagged images | yes |
| file_path_prefix | Prefix for the images file paths. For each image, actual location is determined by combining this prefix with file path | yes |
| probability_prediction_table | **TableMeta** to create a table to put probability distribution to | no |
| labels_of_interest | List of labels which initial prediction should be done for, with their desired thresholds | no |
| default_visual_threshold | Default threshold for labels | no |
| iou_threshold | IoU threshold values for initial prediction evaluation | no |

EvaluateResult
------
| Field name | Description | Mandatory |
|---|---|---|
| images | List of tagged images. Used to see the accuracy of the model | yes |
| confusion_matrix | **ConfusionMatrix** | yes |
| map | Mean average precision | no |
| data_fetch_time | Time required to fetch input data from remote storage (s3) to be used by local processes (in seconds) | yes |
| load_model_time | Time required to load model (in seconds) | yes |
| score_time | Time required to score (in seconds) | yes |
| pipeline_timings | Map of times spent on different stages of the pipeline. Keys are descriptions and values are times (in seconds) | yes |
| probability_prediction_table_schema | **ProbabilityPredictionTableSchema**. Required if `probabilityPredictionTable` is defined in a request | no |

DeleteRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| model_id | Id of the model | yes |

DeleteResult
------
| Field name | Description | Mandatory |
|---|---|---|
| code | Result code | yes |

DeleteResult#Code
------
| Value | Description |
|---|---|
| DELETED | Model has been successfully deleted |
| NOT_FOUND | No model was found for provided id |
| BEING_USED | Specified model is being used currently in some request |

CVModelImportRequest
-------
| Field name | Description | Mandatory |
|---|---|---|
| path | Location of an s3 file containing a model | yes |
| model_type | **CVModelType** | yes |
| fe_only | Indicates whether the file contains only feature extractor's state | yes |

CVModelImportResult
-------
| Field name | Description | Mandatory |
|---|---|---|
| feature_extractor_reference | Reference to the imported feature extractor. It's not required if a custom model is imported | no |
| cv_model_reference | Reference to the imported model | no |

CVModelType
------
| Field name | Description | Mandatory |
|---|---|---|
| type | **TLModel** or **CustomModel** | yes |

TLModel
------
| Field name | Description | Mandatory |
|---|---|---|
| model_type | **TLModelType** | yes |
| feature_extractor_class_reference | **ClassReference** | no |

CustomModel
------
| Field name | Description | Mandatory |
|---|---|---|
| class_reference | **ClassReference** to create the model | yes |

ClassReference
------
| Field name | Description | Mandatory |
|---|---|---|
| package_location | Location of a package with model to be load. It's not required if a model is predefined by system library otherwise a package will be installed | no |
| module_name | Name of the Python module contained a model class | yes |
| class_name | Name of the Python class that represents a model | yes |

ModelReference
------
| Field name | Description | Mandatory |
|---|---|---|
| id | Model id | yes |
| file_path | Path to the serialized model| yes |

AutoAugmentationParams
------
| Field name | Description | Mandatory |
|---|---|---|
| augmentations | list of augmentations to apply | yes |
| bloat_factor | Collective bloat factor | no |
| generate_sample_album | When true, corresponding train result should contain sample sequence of images which were created by augmenting input images | yes |
| sample_album_target_prefix | Prefix which should be used to store all processed image files. Effectively, this is location, _where to put result files_. Only make sense when **generate_sample_album** is specified | no |

PredictedTag
------
| Field name | Description | Mandatory |
|---|---|---|
| tag | Tag | yes |
| confidence | Shows how '_confident_' some model is about the tag for the image. It is a double in range from 0 to 1, with 1 being **completely confident** | yes |

PredictedImage
------
| Field name | Description | Mandatory |
|---|---|---|
| image | Reference to the raw image | yes |
| tags | Sequence of tags, predicted for the image | yes |

VideoParams
------
| Field name | Description | Mandatory |
|---|---|---|
| target_video_file_path | File path of the result video, which should be combined from the result tagged images | yes |
| video_assemble_frame_rate | Frame rate of the result video | yes |
| video_assemble_height | Height of the result video | yes |
| video_assemble_width | Width of the result video | yes |

LabelOfInterest
------
Indicates label which client can specify during prediction

| Field name | Description | Mandatory |
|---|---|---|
| label | Name of the label | yes |
| threshold | Threshold which should be used to consider prediction hit for this label. If not present, any internal default value can be used | no |

TLModelType
------
| Field name | Description | Mandatory |
|---|---|---|
| type | Classifier, localizer or autoencoder type class reference | yes |

ProbabilityPredictionTableSchema
------
Represents a schema of table with predicted probabilities

| Field name | Description | Mandatory |
|---|---|---|
| probability_columns | Sequence of **K** probability columns. Means which column names were created in table and which classes those names correspond to | yes |
| image_file_name_column_name | Name of a column containing image file path | yes |
| area_columns | Location of predicted label in the image. Makes sense only for localization | no |

ProbabilityPredictionAreaColumns
------
Represents a location of predicted label in the image

| Field name | Description | Mandatory |
|---|---|---|
| x_min_column_name | Name of a column containing xmin value | yes |
| x_max_column_name | Name of a column containing xmax value | yes |
| y_min_column_name | Name of a column containing ymin value | yes |
| y_max_column_name | Name of a column containing ymax value | yes |
