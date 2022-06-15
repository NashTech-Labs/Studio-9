[Album Data Augmentation](../../src/main/proto/cortex/api/job/album/augmentation.proto)
======

Example messages can be found in [Sample.scala](../../src/main/scala/cortex/api/job/album/augmentation/Sample.scala)

️:warning: For the description of augmentation parameters please refer to https://sentrana.atlassian.net/wiki/spaces/CVRD/pages/590282772/Data+Augmentation+Library :warning: 

AugmentationRequest
------
| Field name | Description | Mandatory |
|---|---|---|
| images | List of images to augment | yes |
| file_path_prefix | Prefix for the images file paths. For each image, actual location is determined by combining this prefix with file path | yes |
| augmentations | List of augmentations to apply with their parameters | yes |
| bloat_factor | Collective bloat factor | no |
| target_prefix | Prefix which should be used to store all processed image files. Effectively, this is location, _where to put result files_ | yes |
| include_original_image | If true, response should also contain original images also in addition to augmented ones | yes |

AugmentedImage
------
| Field name | Description | Mandatory |
|---|---|---|
| image | Image | yes |
| augmentations | Augmentations which were applied to the image with their params | yes |
| file_size | Size of the file for the image | yes |

AugmentationResult
------
| Field name | Description | Mandatory |
|---|---|---|
| original_images | Original images, to which there were no augmentations applied | yes |
| augmented_images | Augmented images | yes |
| data_fetch_time | Time required to fetch input data from remote storage (s3) to be used by local processes (in seconds) | yes |
| augmentation_time | Time required for augmentation (in seconds) | yes |
| pipeline_timings | Map of times spent on different stages of the pipeline. Keys are descriptions and values are times (in seconds) | yes |

AugmentationSummaryCell
------
| Field name | Description | Mandatory |
|---|---|---|
| requested_augmentation | Augmentation parameters requested | yes |
| images_count | Count of images that were created by the augmentation | yes |

AugmentationSummary
------
| Field name | Description | Mandatory |
|---|---|---|
| augmentation_summary_cells | List of **AugmentationSummaryCell** | yes |
