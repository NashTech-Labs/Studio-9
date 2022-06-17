[Job Common Messages](../../src/main/proto/cortex/api/job/common.proto)
======

ClassReference
------
| Field name | Description | Mandatory |
|---|---|---|
| package_location | Location of the package of model to be load if created by user | no |
| module_name | Name of the Python module contained a model class | yes |
| class_name | Name of the Python class that represents a model | yes |

ConfusionMatrix
------
Represents confusion matrix

| Field name | Description | Mandatory |
|---|---|---|
| confusion_matrix_cells | List of **ConfusionMatrixCell** | yes |
| labels | List of labels which are used in confusion matrix | yes |

ConfusionMatrixCell
------
Represents confusion matrix cell

| Field name | Description | Mandatory |
|---|---|---|
| actual_label_index | Actual label index of an image | no |
| predicted_label_index | Predicted label index of an image | no |
| value | Number of this pair in the final result | yes |

File
------
| Field name | Description | Mandatory |
|---|---|---|
| file_path | Path to the file | yes |
| file_size | Size of the file in bytes | yes |
| file_name | Name of the file which will be seen to the end user. In case of S3 image import, this is original name of the file which was defined by user | yes |

FailedFile
------
| Field name | Description | Mandatory |
|---|---|---|
| file_path | Path to the file. Relative to the system (not user!) bucket name and target prefix (see S3ImportRequest) | yes |
| error_message | Reason of why this file is considered failed | no |
| reference_id | Reference identifier to the image which was supplied by the caller in the corresponding request as part of **InputImage** | no |
