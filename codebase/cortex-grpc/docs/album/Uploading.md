[Album Uploading](../../src/main/proto/cortex/api/job/album/uploading.proto)
======

Example messages can be found in [Sample.scala](../../src/main/scala/cortex/api/job/album/uploading/Sample.scala)

InputImage
------
Represents image with specific input path

| Field name | Description | Mandatory |
|---|---|---|
| base_image | Base image information | yes |
| file_size | Expected size of the image file in bytes | yes |

AlbumLabelMode
------
| Value |
|---|
| CLASSIFICATION |
| LOCALIZATION |

S3ImagesImportRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| bucket_name | S3 Bucket name | yes |
| aws_region | AWS region. Possible values are listed [here](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/regions/Regions.html) | yes| 
| aws_access_key | AWS access key | no (an empty string can be passed to denote IAM roles should be used over credentials) |
| aws_secret_key | AWS secret key | no (an empty string can be passed to denote IAM roles should be used over credentials) |
| aws_session_token | AWS session token. May not be present if credentials are permanent | no |
| images_path | Prefix of image files which should be processed. Relative to bucket name. S3 prefix in AWS terminology. **If *images* field is not specified (empty sequence), than ALL the images which are found under this prefix should be processed** | no |
| labels_csv_path | Path to the CSV file which contains images labels. Relative to the bucket name. S3 key in AWS terminology | no |
| labels_csv_file | Binary content of the CSV file which contains images labels | no |
| target_prefix | Prefix which should be used to store all processed image files. Effectively, this is location, _where to put result files_ | yes |
| images | List of input images to process. All of their paths are relative to the images_path | no |
| label_mode | Label mode for the target album. In case of localization, it is expected that result images contain areas in their tags | yes |
| apply_log_transformation | Specifies whether to apply log transformation or not | yes |

S3VideoImportRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| bucket_name | S3 Bucket name | yes |
| aws_region | AWS region. Possible values are listed [here](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/regions/Regions.html) | yes| 
| aws_access_key | AWS access key | no (an empty string can be passed to denote IAM roles should be used over credentials) |
| aws_secret_key | AWS secret key | no (an empty string can be passed to denote IAM roles should be used over credentials) |
| aws_session_token | AWS session token. May not be present if credentials are permanent | no |
| video_path | Path to the video file in the S3 bucket | yes |
| target_prefix | Prefix which should be used to store all processed video frames as image files. Effectively, this is location, _where to put result video frame files_ | yes |
| frame_capture_rate | Specifies which nth frame should be extracted to a separate image | yes |

TagArea
------
| Field name | Description | Mandatory |
|---|---|---|
| top | Top indent from the top-left corner | yes |
| left | Left indent from the top-left corner | yes |
| height | Height | yes |
| width | Width | yes |

Tag
------
| Field name | Description | Mandatory |
|---|---|---|
| label | Label | yes |
| area | Location of this tag in the image | no |

UploadedImage
------
| Field name | Description | Mandatory |
|---|---|---|
| file | File of this image | yes |
| tags | List of the tags for this image | yes |
| metadata | Map which contains arbitrary metadata of this image | no |
| reference_id | Reference identifier to the image which was supplied by the caller in the corresponding request as part of **InputImage** | no |

S3ImagesImportResult
------
| Field name | Description | Mandatory |
|---|---|---|
| files | List of successfully processed files  | yes |
| failed_files | List of files which were failed to be processed | yes |

S3VideoImportResult
------
| Field name | Description | Mandatory |
|---|---|---|
| image_files | List of processed video frame files | yes |
| video_file | Processed video file | yes |
| video_frame_rate | Original frame rate of the processed video file. This value should not be affected by the **frame_extraction_factor** from the corresponding video import request | yes |
| video_height | Video height| yes |
| video_width | Video width | yes |
