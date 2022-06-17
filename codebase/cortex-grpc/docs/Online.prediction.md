[Online prediction](../src/main/proto/cortex/api/job/online.prediction.proto)
======

Example messages can be found in [Sample.scala](../src/main/scala/cortex/api/job/online/prediction/Sample.scala)

Image
------
| Field name | Description | Mandatory |
|---|---|---|
| key | S3 key of this image file | yes |
| file_size | Size of the file in bytes | yes |

PredictRequest
------
Online prediction request

| Field name | Description | Mandatory |
|---|---|---|
| bucket_name | S3 Bucket name | yes |
| aws_region | AWS region. Possible values are listed [here](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/regions/Regions.html) | yes| 
| aws_access_key | AWS access key | no (an empty string can be passed to denote IAM roles should be used over credentials) |
| aws_secret_key | AWS secret key | no (an empty string can be passed to denote IAM roles should be used over credentials) |
| aws_session_token | AWS session token. May not be present if credentials are permanent | no |
| images | Sequence of S3 images to process | yes |
| target_prefix | Prefix which should be used to store all processed image files. Effectively, this is location, _where to put result files_ | yes |
| model_id | Model which should be used for prediction | yes |

PredictResponse
------
Online prediction response

| Field name | Description | Mandatory |
|---|---|---|
| images | Sequence of **LabledImage** | yes |
| failed_files | Sequence of **cortex.api.job.album.uploading.FailedFile** | yes |
| s3_results_csv_path | Path to s3 root folder which contains csv files with sequence of **LabledImage** | yes |

LabledImage
------
Online prediction image which should be uploaded and run through prediction process for label inferring

| Field name | Description | Mandatory |
|---|---|---|
| file_path | Path to the file. Relative to the system (not user!) bucket name and target prefix (see cortex.api.job.album.uploading.S3ImagesImportRequest) | yes |
| file_size | Size of the file in bytes | yes |
| file_name | Original name of the file which was defined by user | yes |
| metadata | Map which contains arbitrary metadata of this image | no |
| label | Label, associated with an image | yes | 
| confidence | Shows how '_confident_' some model is about the label for the image. It is a double in range from 0 to 1, with 1 being **completely confident** | yes |
