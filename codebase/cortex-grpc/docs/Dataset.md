[Dataset](../../src/main/proto/cortex/api/job/dataset.proto)
======

Example messages can be found in [Sample.scala](../../src/main/scala/cortex/api/job/dataset/Sample.scala)

S3DatasetImportRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| bucket_name | S3 Bucket name | yes |
| aws_region | AWS region. Possible values are listed [here](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/regions/Regions.html) | yes|
| aws_access_key | AWS access key | no (an empty string can be passed to denote IAM roles should be used over credentials) |
| aws_secret_key | AWS secret key | no (an empty string can be passed to denote IAM roles should be used over credentials) |
| aws_session_token | AWS session token. May not be present if credentials are permanent | no |
| dataset_path | Path to the dataset file in the S3 bucket | yes |
| target_prefix | Prefix which should be used to store all dataset files. | yes |


S3DatasetExportRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| bucket_name | S3 Bucket name | yes |
| aws_region | AWS region. Possible values are listed [here](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/regions/Regions.html) | yes|
| aws_access_key | AWS access key | no (an empty string can be passed to denote IAM roles should be used over credentials) |
| aws_secret_key | AWS secret key | no (an empty string can be passed to denote IAM roles should be used over credentials) |
| aws_session_token | AWS session token. May not be present if credentials are permanent | no |
| dataset_path | Path to the dataset file in the S3 bucket | yes |
| target_prefix | Prefix which should be used to store all dataset files. | yes |

UploadedDatasetFile
------
| Field name | Description | Mandatory |
|---|---|---|
| file | File of this dataset | yes |
| metadata | Map which contains arbitrary metadata of this dataset | no |

S3DatasetImportResponse
------
| Field name | Description | Mandatory |
|---|---|---|
| datasets | List of files  | yes |
| failed_files | List of files which were failed to be uploaded | no |

S3DatasetExportResponse
------
| Field name | Description | Mandatory |
|---|---|---|
| datasets | List of files  | yes |
| failed_files | List of files which were failed to be uploaded | no |
